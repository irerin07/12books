package com.irene.twelvebooks.post;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 읽은 분량에 대한 감상. 완독하지 않아도 쓸 수 있고, 구간도 별점도 선택이다.
 *
 * <p>{@code readingId}는 서버가 붙인다. 서재에 없는 책이면 {@code READING}으로 만들어 연결하므로
 * 여기서는 이미 정해진 값을 받기만 한다 — "책 담기를 잊어도 글은 써진다"(spec.md §1.4).
 *
 * <p>{@code bookId}는 reading을 타고 가면 알 수 있는데도 직접 갖는다. 책별 목록이 가장 잦은
 * 조회라 조인 없이 인덱스 하나로 끝나야 한다.
 *
 * <p>카운터는 여기서 올리지 않는다. 읽고-더하고-쓰면 동시 요청에 유실되므로 Phase 6이
 * 원자적 UPDATE로 갱신한다.
 */
@Entity
@Table(name = "posts")
public class Post extends BaseTimeEntity {

	private static final int MAX_CONTENT_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(name = "book_id", nullable = false)
	private Long bookId;

	/** 서재에서 뺀 뒤에도 글은 남는다. 그때 이 값만 비워진다(FK on delete set null). */
	@Column(name = "reading_id")
	private Long readingId;

	@Column(nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	@Column(name = "from_page")
	private Integer fromPage;

	@Column(name = "to_page")
	private Integer toPage;

	@Column(nullable = false)
	private boolean spoiler;

	@Column(name = "like_count", nullable = false)
	private int likeCount;

	@Column(name = "comment_count", nullable = false)
	private int commentCount;

	protected Post() {
	}

	public static Post write(Long authorId, Long bookId, Long readingId, String content,
			Integer fromPage, Integer toPage, boolean spoiler) {
		require(content != null && !content.isBlank(), "본문은 비어 있을 수 없습니다");
		require(content.length() <= MAX_CONTENT_LENGTH, "본문은 1000자를 넘을 수 없습니다");
		require(fromPage == null || fromPage >= 1, "읽은 구간은 1쪽부터입니다");
		require(toPage == null || toPage >= 1, "읽은 구간은 1쪽부터입니다");
		require(fromPage == null || toPage == null || fromPage <= toPage,
				"시작 쪽은 끝 쪽보다 뒤일 수 없습니다");

		Post post = new Post();
		post.authorId = authorId;
		post.bookId = bookId;
		post.readingId = readingId;
		post.content = content;
		post.fromPage = fromPage;
		post.toPage = toPage;
		post.spoiler = spoiler;
		post.likeCount = 0;
		post.commentCount = 0;
		return post;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

	public boolean writtenBy(Long candidateUserId) {
		return authorId.equals(candidateUserId);
	}

	public Long getId() {
		return id;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public Long getBookId() {
		return bookId;
	}

	public Long getReadingId() {
		return readingId;
	}

	public String getContent() {
		return content;
	}

	public Integer getFromPage() {
		return fromPage;
	}

	public Integer getToPage() {
		return toPage;
	}

	public boolean isSpoiler() {
		return spoiler;
	}

	public int getLikeCount() {
		return likeCount;
	}

	public int getCommentCount() {
		return commentCount;
	}
}
