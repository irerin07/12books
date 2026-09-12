package com.irene.twelvebooks.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.post.Comment;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.dto.UserSummaryResponse;

import java.time.LocalDateTime;

/**
 * 댓글 한 건. 작성자는 <b>항상</b> 붙는다 — 없으면 목록을 그리는 쪽이 줄마다 다시 요청한다.
 *
 * <p>지울 수 있는지는 싣지 않는다. 규칙이 "댓글 작성자 또는 글 작성자"라서 화면이 이미 아는
 * 두 handle의 비교로 끝나고, 서버가 계산해 실으면 같은 규칙이 두 곳에 생긴다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentResponse(Long id, UserSummaryResponse author, String content,
		LocalDateTime createdAt) {

	public static CommentResponse of(Comment comment, User author) {
		return new CommentResponse(comment.getId(), UserSummaryResponse.from(author),
				comment.getContent(), comment.getCreatedAt());
	}
}
