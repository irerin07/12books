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
import org.springframework.transaction.annotation.Transactional;

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
	@Transactional
	public User signup(SignupRequest request) {
		if (userRepository.findByEmail(request.email()).isPresent()) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		if (userRepository.findByHandle(request.handle()).isPresent()) {
			throw new BusinessException(ErrorCode.HANDLE_ALREADY_EXISTS);
		}
		try {
			return userRepository.saveAndFlush(User.create(
					request.email(),
					passwordEncoder.encode(request.password()),
					request.handle(),
					request.displayName()));
		}
		catch (DataIntegrityViolationException e) {
			// 사전 조회를 통과한 동시 요청. 어느 쪽이 겹쳤는지는 알 수 없으므로 이메일로 답한다.
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
	}

	/**
	 * 이메일이 없는 경우와 비밀번호가 틀린 경우를 <em>구분하지 않는다.</em>
	 * 구분하면 로그인 폼이 계정 존재 여부를 알려주는 조회 도구가 된다.
	 */
	@Transactional(readOnly = true)
	public Tokens login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		return issueTokens(user);
	}

	@Transactional(readOnly = true)
	public Tokens reissue(String refreshToken) {
		Long userId = refreshTokenStore.findUserId(refreshToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		String rotated = refreshTokenStore.rotate(refreshToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		return new Tokens(jwtProvider.createAccessToken(user.getId(), user.getHandle()), rotated);
	}

	public void logout(String refreshToken) {
		refreshTokenStore.revoke(refreshToken);
	}

	private Tokens issueTokens(User user) {
		return new Tokens(
				jwtProvider.createAccessToken(user.getId(), user.getHandle()),
				refreshTokenStore.issue(user.getId()));
	}

	public record Tokens(String accessToken, String refreshToken) {
	}
}
