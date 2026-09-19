package com.irene.twelvebooks.feed;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.common.support.PageSize;
import com.irene.twelvebooks.follow.FollowService;
import com.irene.twelvebooks.post.PostService;
import com.irene.twelvebooks.post.dto.PostResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글이 흐르는 두 화면 — 홈과 팔로잉 전용. 팔로우 관계와 감상평이 여기서 만나므로 어느 한쪽
 * 패키지에 두지 않고 조합만 하는 자리를 따로 뒀다.
 */
@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

	private final PostService postService;
	private final FollowService followService;

	public FeedController(PostService postService, FollowService followService) {
		this.postService = postService;
		this.followService = followService;
	}

	/**
	 * 홈. 아직 팔로우하지 않은 사람들의 글이다 — 내 글과 팔로잉 글은 빠진다.
	 *
	 * <p>팔로잉을 빼므로 {@code /feed/following}과 겹치지 않는다 — <b>같은 팔로우 상태를
	 * 기준으로</b> 그렇다. 각 요청은 독립적으로 조회하며, <b>두 요청 사이의 공통 스냅샷은
	 * 제공하지 않는다.</b> 그사이 팔로우 관계가 바뀌면 두 응답을 합친 결과에 중복이나 누락이
	 * 생길 수 있다. 왜 본인 글을 빼는지는 {@code plan.md} Phase 5에 있다.
	 */
	@GetMapping
	public CursorPage<PostResponse> home(@AuthUser Long userId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.home(userId, followService.followeeIds(userId), cursor, PageSize.clamp(size));
	}

	/** 팔로잉 전용. 내가 고른 사람들의 글만 — 아무도 팔로우하지 않으면 빈다. */
	@GetMapping("/following")
	public CursorPage<PostResponse> following(@AuthUser Long userId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.timeline(userId, followService.followeeIds(userId), cursor, PageSize.clamp(size));
	}

}
