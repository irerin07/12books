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

import java.util.Set;

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
	 * 홈. 팔로우한 사람의 글과 아닌 사람의 글이 <b>섞여</b> 흐르고, 각 글에 팔로잉 여부가 붙는다.
	 * 인스타·트위터의 홈과 같은 모양이다.
	 *
	 * <p>서버가 섞어 주는 이유는 화면 취향이 아니라 기술이다. 팔로잉 목록과 전체 목록을 따로
	 * 받아 클라이언트가 이어 붙이면, 팔로우한 사람의 글이 양쪽에 다 나와 <b>중복</b>되고 커서도
	 * 둘을 따로 굴려야 한다. 한 쿼리·한 커서면 그 문제가 아예 없다.
	 *
	 * <p>내 글은 여기 없다 — {@code GET /users/{handle}/posts}가 준다.
	 */
	@GetMapping
	public CursorPage<PostResponse> home(@AuthUser Long userId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.home(userId, Set.copyOf(followService.followeeIds(userId)),
				cursor, PageSize.clamp(size));
	}

	/**
	 * 팔로잉 전용. 내가 고른 사람들의 글만 — 인스타에서 로고를 눌러 "Following"으로 전환한 화면,
	 * 트위터의 "Following" 탭에 해당한다. 아무도 팔로우하지 않으면 빈다.
	 */
	@GetMapping("/following")
	public CursorPage<PostResponse> following(@AuthUser Long userId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.timeline(followService.followeeIds(userId), cursor, PageSize.clamp(size));
	}

}
