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
	public ProfileResponse profile(@AuthUser Long viewerId, @PathVariable String handle) {
		return withRelation(userService.getByHandle(handle), viewerId);
	}

	/**
	 * 내 프로필이므로 {@code isFollowing}은 항상 거짓이다 — 자기 자신은 팔로우할 수 없다.
	 * 그래도 같은 응답 모양을 쓰는 편이 낫다. 화면이 프로필 하나를 그리는 코드를 둘로 나누지
	 * 않아도 된다.
	 */
	@PatchMapping("/me")
	public ProfileResponse updateMe(@AuthUser Long userId, @Valid @RequestBody UpdateProfileRequest request) {
		return withRelation(userService.updateProfile(userId, request), userId);
	}

	/**
	 * 관계 정보는 팔로우 쪽이 답한다. {@code user}가 {@code follow}를 알게 하면 두 패키지가
	 * 서로를 참조하게 되므로, 조합만 여기서 한다.
	 */
	private ProfileResponse withRelation(User user, Long viewerId) {
		return ProfileResponse.of(user,
				followService.followerCount(user.getId()),
				followService.followingCount(user.getId()),
				followService.isFollowing(viewerId, user.getId()));
	}
}
