package com.irene.twelvebooks.post;

import com.irene.twelvebooks.auth.AuthUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좋아요. 응답 본문이 없는 것은 화면이 이미 아는 값이기 때문이다 — 누른 직후의 상태는
 * "내가 눌렀고 카운터가 하나 올랐다"로 정해져 있어, 서버가 되돌려줘도 화면이 다시 그릴 것이 없다.
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}/likes")
public class PostLikeController {

	private final PostLikeService postLikeService;

	public PostLikeController(PostLikeService postLikeService) {
		this.postLikeService = postLikeService;
	}

	@PostMapping
	public ResponseEntity<Void> like(@AuthUser Long userId, @PathVariable Long postId) {
		postLikeService.like(userId, postId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> unlike(@AuthUser Long userId, @PathVariable Long postId) {
		postLikeService.unlike(userId, postId);
		return ResponseEntity.noContent().build();
	}
}
