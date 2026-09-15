package com.irene.twelvebooks.user;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.auth.RefreshTokenStore;
import com.irene.twelvebooks.user.dto.UpdateProfileRequest;
import com.irene.twelvebooks.user.dto.WithdrawRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenStore refreshTokenStore;
	private final Clock clock;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			RefreshTokenStore refreshTokenStore, Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenStore = refreshTokenStore;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public User getByHandle(String handle) {
		return userRepository.findByHandle(handle)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

	@Transactional
	public User updateProfile(Long userId, UpdateProfileRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		user.updateProfile(request.displayName(), request.bio(), request.avatarUrl());
		return user;
	}

	/**
	 * 탈퇴한다 — <b>행은 지우지 않는다.</b> 계정에 시각을 세우면 그 사람과 관련된 것이
	 * 조회에서 빠진다({@code V13}).
	 *
	 * <p>비밀번호를 다시 확인한다. 되돌릴 수 없는 일이라, 토큰만으로 받으면 자리를 비운 사이
	 * 남이 브라우저를 만지는 것으로 계정이 사라진다.
	 *
	 * <p>세션은 전부 끊는다. 남겨 두면 탈퇴한 계정으로 계속 재발급된다 — 로그인은 막히는데
	 * 이미 들고 있던 토큰만 살아 있는 상태가 된다.
	 */
	@Transactional
	public void withdraw(Long userId, WithdrawRequest request) {
		User user = userRepository.findById(userId)
				.filter(candidate -> !candidate.isWithdrawn())
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			// 로그인 실패와 같은 코드로 답한다. 여기서만 다른 말을 할 이유가 없다.
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		user.withdraw(LocalDateTime.now(clock));
		refreshTokenStore.revokeAll(userId);
	}
}
