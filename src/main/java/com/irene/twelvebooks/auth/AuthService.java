package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.auth.dto.LoginRequest;
import com.irene.twelvebooks.auth.dto.PasswordResetConfirmRequest;
import com.irene.twelvebooks.auth.dto.PasswordResetRequest;
import com.irene.twelvebooks.auth.dto.SignupRequest;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이 서비스에는 메서드 단위 트랜잭션을 걸지 않는다.
 *
 * <p>BCrypt는 의도적으로 느린 연산이고 Redis 발급은 외부 왕복이다. 메서드 전체를 트랜잭션으로
 * 묶으면 그동안 DB 커넥션을 붙잡게 되어, 로그인이 몰릴 때 커넥션 풀부터 마른다.
 * 각 리포지토리 호출은 이미 자기 트랜잭션 안에서 실행되고, 여기서 여러 쓰기를 원자적으로
 * 묶어야 하는 지점은 없다 — 가입의 중복 방어선은 트랜잭션이 아니라 DB 유니크 제약이다.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final RefreshTokenStore refreshTokenStore;
	private final PasswordResetTokenStore passwordResetTokenStore;
	private final PasswordResetMailer passwordResetMailer;
	private final CredentialVersions credentialVersions;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
			RefreshTokenStore refreshTokenStore, PasswordResetTokenStore passwordResetTokenStore,
			PasswordResetMailer passwordResetMailer, CredentialVersions credentialVersions) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtProvider = jwtProvider;
		this.refreshTokenStore = refreshTokenStore;
		this.passwordResetTokenStore = passwordResetTokenStore;
		this.passwordResetMailer = passwordResetMailer;
		this.credentialVersions = credentialVersions;
	}

	/**
	 * 재설정 링크를 보낸다 — <b>계정이 있을 때만.</b> 없으면 조용히 아무것도 하지 않는다.
	 *
	 * <p>호출부는 어느 쪽이든 204를 돌려준다. "가입되지 않은 이메일입니다"로 답하면 그 한
	 * 줄이 <b>계정 열거 통로</b>가 된다 — 이메일 목록을 넣어 보며 누가 이 서비스를 쓰는지
	 * 알아낼 수 있고, 그건 서비스 성격에 따라 그 자체로 민감한 정보다.
	 *
	 * <p>발송도 여기서 기다리지 않는다({@link PasswordResetMailer}). 기다리면 <b>응답 시간의
	 * 차이만으로</b> 계정 유무가 드러난다 — 있는 주소는 SMTP 왕복만큼 느리다.
	 */
	public void requestPasswordReset(PasswordResetRequest request) {
		userRepository.findByEmail(request.email()).ifPresent(user -> {
			String token = passwordResetTokenStore.issue(user.getId());
			try {
				passwordResetMailer.send(user.getEmail(), token);
			}
			catch (TaskRejectedException e) {
				// 대기열이 가득 차면 거절은 @Async 메서드 <b>안</b>이 아니라 여기서 난다.
				// 그대로 두면 500이 나가고, 없는 주소는 204라서 응답 코드만으로 계정 유무가
				// 드러난다 — 언제나 204라는 계약이 바로 그 통로가 된다.
				log.warn("비밀번호 재설정 메일을 대기열에 넣지 못했습니다");
			}
		});
	}

	/**
	 * 토큰을 쓰고 비밀번호를 바꾼다.
	 *
	 * <p>바꾼 뒤 <b>그 사람의 세션을 전부 끊는다.</b> 비밀번호를 바꾸는 이유가 보통 탈취이기
	 * 때문이다 — 훔친 기기의 refresh가 그대로 살아 있으면 바꾼 의미가 없다. 본인의 다른 기기도
	 * 함께 끊기지만, 다시 로그인하면 된다.
	 *
	 * <p>토큰을 먼저 쓰고(=지우고) 비밀번호를 바꾼다. 순서를 뒤집으면 바꾼 뒤에 토큰 소모가
	 * 실패했을 때 같은 링크가 한 번 더 통한다.
	 */
	@Transactional
	public void confirmPasswordReset(PasswordResetConfirmRequest request) {
		Long userId = passwordResetTokenStore.consume(request.token())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));
		User user = userRepository.findById(userId)
				// 토큰을 받은 뒤 계정이 사라진 경우. 링크 문제로 답한다 — 여기서 "없는
				// 사용자"라고 알려 주면 그것도 계정 유무를 흘리는 통로가 된다.
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));

		user.changePassword(passwordEncoder.encode(request.password()));
		// 번호를 먼저 올린다. 이 순간부터 옛 비밀번호로 만들어지는 세션은 첫 재발급에서 걸린다.
		credentialVersions.bump(userId);
		refreshTokenStore.revokeAll(userId);
	}

	/**
	 * 중복은 두 겹으로 막는다. 사전 조회는 어느 필드가 겹쳤는지 알려주기 위한 것이고,
	 * 실제 방어선은 DB 유니크 제약이다 — 두 요청이 동시에 조회를 통과할 수 있기 때문이다.
	 */
	public User signup(SignupRequest request) {
		if (userRepository.findByEmail(request.email()).isPresent()) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		if (userRepository.findByHandle(request.handle()).isPresent()) {
			throw new BusinessException(ErrorCode.HANDLE_ALREADY_EXISTS);
		}
		String passwordHash = passwordEncoder.encode(request.password());
		try {
			return userRepository.saveAndFlush(
					User.create(request.email(), passwordHash, request.handle(), request.displayName()));
		}
		catch (DataIntegrityViolationException e) {
			// 사전 조회를 함께 통과한 동시 요청. 어느 제약이 터졌는지를 보고 답해야
			// 사용자가 고칠 수 있는 안내가 된다.
			throw new BusinessException(DuplicateUserKeys.errorCodeOf(e));
		}
	}

	/**
	 * 이메일이 없는 경우와 비밀번호가 틀린 경우를 <em>구분하지 않는다.</em>
	 * 구분하면 로그인 폼이 계정 존재 여부를 알려주는 조회 도구가 된다.
	 */
	/**
	 * <p>자격증명 번호를 <b>비밀번호를 확인하기 전에</b> 읽는다. 발급 직전에 읽으면, 검증과
	 * 발급 사이에 재설정이 끝났을 때 그 세션이 <b>새 번호를 달고</b> 살아남는다 — 옛 비밀번호로
	 * 만들어진 세션인데 무효화 대상에도, 번호 검사에도 걸리지 않는다.
	 */
	public Tokens login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
		String credentialVersion = credentialVersions.current(user.getId());
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		return new Tokens(
				jwtProvider.createAccessToken(user.getId(), user.getHandle()),
				refreshTokenStore.issue(user.getId(), credentialVersion));
	}

	/**
	 * 낙관적 검증이다 — Redis 조회로 주인을 얻고, DB로 사용자를 확인하고, 마지막에 교체한다.
	 *
	 * <p>순서가 핵심이다. 교체가 먼저면 직후 DB 조회가 실패했을 때 옛 토큰은 이미 폐기되고
	 * 새 토큰은 클라이언트에 닿지 못해 세션만 사라진다. 지금 순서라면 DB가 실패해도 토큰이
	 * 그대로라 그냥 다시 시도하면 된다.
	 *
	 * <p>사전 조회와 교체 사이에 다른 요청이 먼저 교체하더라도, 마지막 스크립트가 옛 키의 부재를
	 * 확인하고 실패하므로 두 요청이 모두 성공하는 일은 없다. Redis 왕복 2회는 이 구조에서
	 * 의도한 최소 비용이다 — 1회로 줄이려면 교체를 앞으로 당겨야 하고 위의 세션 유실이 되돌아온다.
	 */
	public Tokens reissue(String refreshToken) {
		Long userId = refreshTokenStore.findUserId(refreshToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		// 옛 비밀번호로 만들어진 세션을 여기서 걸러 낸다. 무효화 직후에 도착한 발급은
		// 끊긴 적이 없어 계속 재발급되는데, 그 세션은 그때의 번호를 들고 있다.
		String issuedWith = refreshTokenStore.findCredentialVersion(refreshToken)
				.orElse(CredentialVersions.INITIAL);
		if (!issuedWith.equals(credentialVersions.current(userId))) {
			refreshTokenStore.revoke(refreshToken);
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		String rotated = refreshTokenStore.rotate(refreshToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		return new Tokens(jwtProvider.createAccessToken(user.getId(), user.getHandle()), rotated);
	}

	public void logout(String refreshToken) {
		refreshTokenStore.revoke(refreshToken);
	}

	public record Tokens(String accessToken, String refreshToken) {
	}
}
