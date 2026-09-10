package com.irene.twelvebooks.follow;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.common.support.PageSize;
import com.irene.twelvebooks.follow.dto.FollowItemResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팔로우 관계. 돌려줄 표현이 따로 없어 생성·삭제 모두 204다 —
 * 관계는 "있다/없다"가 전부이고, 그 결과는 목록과 프로필 수로 확인된다.
 */
@RestController
@RequestMapping("/api/v1/users/{handle}")
public class FollowController {

	private final FollowService followService;

	public FollowController(FollowService followService) {
		this.followService = followService;
	}

	@PostMapping("/follow")
	public ResponseEntity<Void> follow(@AuthUser Long userId, @PathVariable String handle) {
		followService.follow(userId, handle);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/follow")
	public ResponseEntity<Void> unfollow(@AuthUser Long userId, @PathVariable String handle) {
		followService.unfollow(userId, handle);
		return ResponseEntity.noContent().build();
	}

	/**
	 * 목록의 {@code isFollowing}은 <b>목록 주인이 아니라 보는 사람</b> 기준이다.
	 * 남의 팔로워 목록을 볼 때도 각 줄의 버튼은 내가 그 사람을 팔로우 중인지를 따라야 한다.
	 */
	@GetMapping("/followers")
	public CursorPage<FollowItemResponse> followers(@AuthUser Long viewerId, @PathVariable String handle,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return followService.followers(handle, viewerId, cursor, PageSize.clamp(size));
	}

	@GetMapping("/followings")
	public CursorPage<FollowItemResponse> followings(@AuthUser Long viewerId, @PathVariable String handle,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return followService.followings(handle, viewerId, cursor, PageSize.clamp(size));
	}
}
