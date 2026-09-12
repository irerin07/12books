package com.irene.twelvebooks.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.dto.BookResponse;
import com.irene.twelvebooks.post.Post;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.dto.UserSummaryResponse;

import java.time.LocalDateTime;

/**
 * 감상평 한 건. 단건 조회와 목록이 같은 모양을 쓴다 — 클라이언트가 화면마다 다른 구조를
 * 다루지 않아도 되고, 목록에만 빠진 필드가 생기지도 않는다.
 *
 * <p>작성자와 책은 <b>항상</b> 붙는다. 없으면 목록을 그리는 쪽이 항목마다 다시 요청하게 된다.
 *
 * <p>스포일러는 서버가 가리지 않고 플래그만 그대로 내보낸다. 가리는 방식(블러·펼치기)은
 * 화면이 정할 일이고, 서버가 본문을 지우면 작성자 본인도 자기 글을 볼 수 없다.
 *
 * <p>{@code likedByMe}는 <b>보는 사람 기준</b>이라 글마다 고정된 값이 아니다. 없으면 화면이
 * 하트를 처음 그릴 때 어느 상태로 둘지 정할 수 없다. 목록에서도 빠지지 않는 것은, 계산하지
 * 않는 자리를 만들면 화면이 {@code undefined}와 {@code false}를 구분해야 하기 때문이다 —
 * 이 응답이 나가는 모든 경로가 값을 채운다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostResponse(
		Long id,
		UserSummaryResponse author,
		BookResponse book,
		Long readingId,
		String content,
		Integer fromPage,
		Integer toPage,
		boolean spoiler,
		int likeCount,
		int commentCount,
		boolean likedByMe,
		LocalDateTime createdAt) {

	public static PostResponse of(Post post, User author, Book book, boolean likedByMe) {
		return new PostResponse(post.getId(), UserSummaryResponse.from(author), BookResponse.from(book),
				post.getReadingId(), post.getContent(), post.getFromPage(), post.getToPage(),
				post.isSpoiler(), post.getLikeCount(), post.getCommentCount(), likedByMe,
				post.getCreatedAt());
	}
}
