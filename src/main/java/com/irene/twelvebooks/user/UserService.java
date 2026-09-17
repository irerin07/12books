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

	/** 계정에 탈퇴 표시를 남긴 뒤 refresh 세션을 정리한다. 댓글 수는 조회 시 계산한다. */
	public void withdraw(Long userId, WithdrawRequest request) {
		User user = userRepository.findById(userId)
				.filter(candidate -> !candidate.isWithdrawn())
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			// 로그인 실패와 같은 코드로 답한다. 여기서만 다른 말을 할 이유가 없다.
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		if (userRepository.withdraw(userId, LocalDateTime.now(clock)) == 0) {
			// 그사이 다른 요청이 끝냈다. 결과는 같으므로 성공으로 답한다.
			return;
		}
		refreshTokenStore.revokeAll(userId);
	}
}
