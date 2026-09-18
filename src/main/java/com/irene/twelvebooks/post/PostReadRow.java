package com.irene.twelvebooks.post;

import com.irene.twelvebooks.book.dto.BookResponse;
import com.irene.twelvebooks.post.dto.PostResponse;
import com.irene.twelvebooks.user.dto.UserSummaryResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 표시 필드와 공개 댓글 수를 같은 조회에서 읽는다. */
public record PostReadRow(Long id, String handle, String displayName, String avatarUrl,
        Long bookId, String isbn13, String title, String authors, String publisher,
        String thumbnailUrl, LocalDate publishedAt, Long readingId, String content,
        Integer fromPage, Integer toPage, boolean spoiler, int likeCount,
        long commentCount, boolean likedByMe, LocalDateTime createdAt) {
    public PostResponse toResponse() {
        return new PostResponse(id, new UserSummaryResponse(handle, displayName, avatarUrl),
                new BookResponse(bookId, isbn13, title, authors, publisher, thumbnailUrl, publishedAt),
                readingId, content, fromPage, toPage, spoiler, likeCount, commentCount, likedByMe, createdAt);
    }
}
