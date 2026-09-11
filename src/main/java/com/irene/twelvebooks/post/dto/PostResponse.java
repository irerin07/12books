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
		LocalDateTime createdAt,
		Boolean followingAuthor) {

	/**
	 * 관계를 계산하지 않는 자리에서 쓴다. {@code followingAuthor}는 <b>응답에서 아예 빠진다</b> —
	 * 계산하지 않은 값을 {@code false}로 실으면 "팔로우하지 않았다"는 거짓말이 된다.
	 */
	public static PostResponse of(Post post, User author, Book book) {
		return of(post, author, book, null);
	}

	/**
	 * @param followingAuthor 보는 사람이 작성자를 팔로우 중인지. 홈이 팔로잉 글과 아닌 글을
	 *                        섞어 주므로, 화면이 둘을 구분해 표시하려면 이 값이 필요하다.
	 *                        {@code null}이면 이 응답에서는 계산하지 않았다는 뜻이다.
	 */
	public static PostResponse of(Post post, User author, Book book, Boolean followingAuthor) {
		return new PostResponse(post.getId(), UserSummaryResponse.from(author), BookResponse.from(book),
				post.getReadingId(), post.getContent(), post.getFromPage(), post.getToPage(),
				post.isSpoiler(), post.getLikeCount(), post.getCommentCount(), post.getCreatedAt(),
				followingAuthor);
	}
}
