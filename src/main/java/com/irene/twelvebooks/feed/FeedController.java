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
	 * 홈. 팔로잉 글과 아닌 글이 섞여 흐르고 각 글에 팔로잉 여부가 붙는다. 내 글은 여기 없다 —
	 * {@code GET /users/{handle}/posts}가 준다.
	 *
	 * <p>왜 서버가 섞는지, 왜 본인 글을 빼는지는 {@code plan.md} Phase 5에 있다.
	 * 여기서는 관계를 <b>그 페이지의 작성자에 대해서만</b> 묻는다는 점만 짚어 둔다 —
	 * 팔로잉 전체를 끌어오면 비용이 팔로잉 수에 비례한다.
	 */
	@GetMapping
	public CursorPage<PostResponse> home(@AuthUser Long userId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.home(userId, cursor, PageSize.clamp(size),
				authorIds -> followService.followedAmong(userId, authorIds));
	}

	/** 팔로잉 전용. 내가 고른 사람들의 글만 — 아무도 팔로우하지 않으면 빈다. */
	@GetMapping("/following")
	public CursorPage<PostResponse> following(@AuthUser Long userId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.timeline(followService.followeeIds(userId), cursor, PageSize.clamp(size));
	}

}
