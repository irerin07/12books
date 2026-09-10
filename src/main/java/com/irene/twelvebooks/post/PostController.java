package com.irene.twelvebooks.post;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.common.support.PageSize;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.post.dto.PostCreateRequest;
import com.irene.twelvebooks.post.dto.PostResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감상평과 그것을 모아 보여주는 목록들. 책별 목록과 탐색 피드가 {@code /posts} 밖의 경로를
 * 쓰지만 같은 자원을 다루므로 여기 함께 둔다 — 서재가 {@code /users/{handle}/library}를
 * {@code LibraryController}에 두는 것과 같다.
 */
@RestController
@RequestMapping("/api/v1")
public class PostController {

	private final PostService postService;

	public PostController(PostService postService) {
		this.postService = postService;
	}

	@PostMapping("/posts")
	public ResponseEntity<PostResponse> write(@AuthUser Long userId,
			@Valid @RequestBody PostCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(postService.write(userId, request));
	}

	@GetMapping("/posts/{id}")
	public PostResponse read(@PathVariable Long id) {
		return postService.read(id);
	}

	@DeleteMapping("/posts/{id}")
	public ResponseEntity<Void> remove(@AuthUser Long userId, @PathVariable Long id) {
		postService.remove(userId, id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/books/{bookId}/posts")
	public CursorPage<PostResponse> byBook(@PathVariable Long bookId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.byBook(bookId, cursor, PageSize.clamp(size));
	}

	/**
	 * 전체 최신순. 팔로우 관계가 없어도 볼 것이 있어야 신규 사용자가 빈 화면을 보지 않는다.
	 * 팔로잉 타임라인({@code GET /feed})은 팔로우가 생기는 Phase 5에서 더한다.
	 */
	@GetMapping("/feed/explore")
	public CursorPage<PostResponse> explore(@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return postService.explore(cursor, PageSize.clamp(size));
	}
}
