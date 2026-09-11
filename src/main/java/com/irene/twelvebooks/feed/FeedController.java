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
 * 글이 흐르는 두 갈래. 팔로우 관계와 감상평이 여기서 만나므로 어느 한쪽 패키지에 두지 않고
 * 조합만 하는 자리를 따로 뒀다.
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
	 * 내가 고른 사람들의 글. <b>내 글은 섞이지 않는다</b> — 그건
	 * {@code GET /users/{handle}/posts}가 따로 준다.
	 */
	@GetMapping
	public CursorPage<PostResponse> timeline(@AuthUser Long userId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.timeline(followService.followeeIds(userId), cursor, PageSize.clamp(size));
	}

	/** 전체 최신순. 팔로우 관계가 없어도 볼 것이 있어야 신규 사용자가 빈 화면을 보지 않는다. */
	@GetMapping("/explore")
	public CursorPage<PostResponse> explore(@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.explore(cursor, PageSize.clamp(size));
	}
}
