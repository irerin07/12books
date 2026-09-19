package com.irene.twelvebooks.follow;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.block.BlockGuard;
import com.irene.twelvebooks.common.ratelimit.RateLimit;
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

	private final BlockGuard blockGuard;

	public FollowController(FollowService followService, BlockGuard blockGuard) {
		this.followService = followService;
		this.blockGuard = blockGuard;
	}

	/**
	 * <b>차단을 보지 않는다.</b> 팔로우는 "이 사람 글을 받아 보겠다"는 신청이고, 차단은 그
	 * 신청을 거절하는 것이 아니라 <b>내용 자체를 안 보내는 것</b>이다. 차단된 사이에서
	 * 팔로우가 걸려도 글·댓글·프로필은 그대로 가려진다.
	 *
	 * <p>막으면 앞뒤가 안 맞는다 — 차단은 <b>상대가 건 팔로우를 남긴다</b>(내가 건 것만
	 * 끊는다). 같은 관계가 유지는 되는데 새로 만들 수는 없으면, 상대가 실수로 언팔한 순간
	 * 차단이 풀릴 때까지 돌아올 수 없다.
	 *
	 * <p>읽기 경로에는 가드가 있고 여기에는 없다. 그 경계가 규칙이다 — 가리는 것은 내용이고,
	 * 자기 관계를 바꾸는 쓰기는 본인 몫이다.
	 */
	@RateLimit(name = "follow", limit = 60, windowSeconds = 60, scope = RateLimit.Scope.USER)
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
		blockGuard.requireVisible(viewerId, handle);
		return followService.followers(handle, viewerId, cursor, PageSize.clamp(size));
	}

	@GetMapping("/followings")
	public CursorPage<FollowItemResponse> followings(@AuthUser Long viewerId, @PathVariable String handle,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		blockGuard.requireVisible(viewerId, handle);
		return followService.followings(handle, viewerId, cursor, PageSize.clamp(size));
	}
}
