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

	/**
	 * 프로필을 고친다.
	 *
	 * <p>탈퇴한 계정은 거절한다. access의 남은 수명은 허용하지만, 그 토큰으로 <b>탈퇴한 행을
	 * 계속 고칠 수 있는 것</b>은 다른 문제다.
	 *
	 * <p>탈퇴와 "없는 사용자"를 <b>같은 답으로</b> 돌려준다. 사용자에게 탈퇴는 삭제이고, 행이
	 * 남아 있다는 것은 보관을 위한 우리 쪽 사정이다. 여기서만 401을 주면 "계정은 있는데 권한이
	 * 없다"가 되어 지워지지 않았음을 알려주는 꼴이 된다 — 하드 삭제였다면 이 조회가 비어
	 * 404가 났을 자리다.
	 */
	@Transactional
	public User updateProfile(Long userId, UpdateProfileRequest request) {
		User user = userRepository.findById(userId)
				.filter(candidate -> !candidate.isWithdrawn())
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
