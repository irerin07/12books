package com.irene.twelvebooks.user;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.follow.FollowService;
import com.irene.twelvebooks.user.dto.ProfileResponse;
import com.irene.twelvebooks.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UserController {

	private final UserService userService;
	private final FollowService followService;

	public UserController(UserService userService, FollowService followService) {
		this.userService = userService;
		this.followService = followService;
	}

	@GetMapping("/users/{handle}")
	public ProfileResponse profile(@PathVariable String handle) {
		return withCounts(userService.getByHandle(handle));
	}

	@PatchMapping("/me")
	public ProfileResponse updateMe(@AuthUser Long userId, @Valid @RequestBody UpdateProfileRequest request) {
		return withCounts(userService.updateProfile(userId, request));
	}

	/**
	 * 관계 수는 팔로우 쪽이 세어 준다. {@code user}가 {@code follow}를 알게 하면 두 패키지가
	 * 서로를 참조하게 되므로, 조합만 여기서 한다.
	 */
	private ProfileResponse withCounts(User user) {
		return ProfileResponse.of(user, followService.followerCount(user.getId()),
				followService.followingCount(user.getId()));
	}
}
