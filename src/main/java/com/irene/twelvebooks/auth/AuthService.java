package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.auth.dto.LoginRequest;
import com.irene.twelvebooks.auth.dto.SignupRequest;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final RefreshTokenStore refreshTokenStore;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
			RefreshTokenStore refreshTokenStore) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtProvider = jwtProvider;
		this.refreshTokenStore = refreshTokenStore;
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
	public Tokens login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		return new Tokens(
				jwtProvider.createAccessToken(user.getId(), user.getHandle()),
				refreshTokenStore.issue(user.getId()));
	}

	/**
	 * 사용자를 먼저 확인하고 <em>마지막에</em> 교체한다. 순서가 반대면 교체 직후 DB 조회가 실패했을 때
	 * 옛 토큰은 이미 폐기되고 새 토큰은 클라이언트에 닿지 못해 세션만 사라진다.
	 */
	public Tokens reissue(String refreshToken) {
		Long userId = refreshTokenStore.findUserId(refreshToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		RefreshTokenStore.Session rotated = refreshTokenStore.rotate(refreshToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		return new Tokens(jwtProvider.createAccessToken(user.getId(), user.getHandle()), rotated.token());
	}

	public void logout(String refreshToken) {
		refreshTokenStore.revoke(refreshToken);
	}

	public record Tokens(String accessToken, String refreshToken) {
	}
}
