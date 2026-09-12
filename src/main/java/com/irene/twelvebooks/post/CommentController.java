package com.irene.twelvebooks.post;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.common.support.PageSize;
import com.irene.twelvebooks.post.dto.CommentCreateRequest;
import com.irene.twelvebooks.post.dto.CommentResponse;
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
 * 댓글. 작성·조회는 글에 매달리지만 삭제는 {@code /comments/{id}}다 — 댓글 id 하나로 찾을 수
 * 있는데 글 id까지 요구하면, 화면이 둘을 함께 들고 다녀야 하고 어긋난 조합을 보내면 어떻게
 * 답할지가 또 규칙이 된다.
 */
@RestController
@RequestMapping("/api/v1")
public class CommentController {

	private final CommentService commentService;

	public CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	@PostMapping("/posts/{postId}/comments")
	public ResponseEntity<CommentResponse> write(@AuthUser Long userId, @PathVariable Long postId,
			@Valid @RequestBody CommentCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(commentService.write(userId, postId, request));
	}

	@GetMapping("/posts/{postId}/comments")
	public CursorPage<CommentResponse> byPost(@PathVariable Long postId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return commentService.byPost(postId, cursor, PageSize.clamp(size));
	}

	@DeleteMapping("/comments/{id}")
	public ResponseEntity<Void> remove(@AuthUser Long userId, @PathVariable Long id) {
		commentService.remove(userId, id);
		return ResponseEntity.noContent().build();
	}
}
