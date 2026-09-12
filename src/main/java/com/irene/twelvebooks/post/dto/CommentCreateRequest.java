package com.irene.twelvebooks.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 작성. 본문 하나뿐이다 — 대댓글이 없어 부모를 가리킬 필요가 없고, 어느 글에 다는지는
 * 경로가 말한다.
 */
public record CommentCreateRequest(
		@NotBlank @Size(max = 500) String content) {
}
