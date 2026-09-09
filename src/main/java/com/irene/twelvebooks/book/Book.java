package com.irene.twelvebooks.book;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * 내부에 확정된 책. 검색 결과는 여기 들어오지 않는다 — 누군가 실제로 담은 책만 쌓인다.
 *
 * <p>{@code isbn13}과 {@code sourceKey}는 둘 다 nullable이면서 unique다. MySQL이 NULL 중복을
 * 허용하므로 이 조합이 성립한다 — ISBN이 있는 책은 앞쪽으로, 없는 책은 뒤쪽으로 유일성을 지킨다.
 * 정확히 하나만 있어야 하고, 그 갈림이 곧 두 정적 팩토리다.
 *
 * <p>총 쪽수는 여기 없다. 카카오가 주지 않아 사용자가 채워야 하는데, 공용 테이블을 사용자가
 * 고치게 하면 등록 서명으로 막은 오염 경로가 되살아난다. 쪽수는 {@code readings}가 갖는다.
 */
@Entity
@Table(name = "books")
public class Book extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, length = 13)
	private String isbn13;

	@Column(name = "source_key", unique = true, length = 64)
	private String sourceKey;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(nullable = false, length = 500)
	private String authors;

	@Column(length = 200)
	private String publisher;

	@Column(name = "thumbnail_url", length = 500)
	private String thumbnailUrl;

	@Column(name = "published_at")
	private LocalDate publishedAt;

	protected Book() {
	}

	private Book(String isbn13, String sourceKey, String title, String authors, String publisher,
			String thumbnailUrl, LocalDate publishedAt) {
		require(title != null && !title.isBlank(), "title은 비어 있을 수 없습니다");
		require(authors != null && !authors.isBlank(), "authors는 비어 있을 수 없습니다");
		require((isbn13 == null) != (sourceKey == null), "isbn13과 sourceKey 중 정확히 하나여야 합니다");
		this.isbn13 = isbn13;
		this.sourceKey = sourceKey;
		this.title = title;
		this.authors = authors;
		this.publisher = publisher;
		this.thumbnailUrl = thumbnailUrl;
		this.publishedAt = publishedAt;
	}

	/** ISBN이 있는 책. 유일성은 ISBN이 지킨다. */
	public static Book withIsbn13(String isbn13, String title, String authors, String publisher,
			String thumbnailUrl, LocalDate publishedAt) {
		require(isbn13 != null, "isbn13이 필요합니다");
		return new Book(isbn13, null, title, authors, publisher, thumbnailUrl, publishedAt);
	}

	/** ISBN이 없는 책. 제목·저자·출판사에서 만든 sourceKey가 유일성을 대신 지킨다. */
	public static Book withSourceKey(String sourceKey, String title, String authors, String publisher,
			String thumbnailUrl, LocalDate publishedAt) {
		require(sourceKey != null, "sourceKey가 필요합니다");
		return new Book(null, sourceKey, title, authors, publisher, thumbnailUrl, publishedAt);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

	public Long getId() {
		return id;
	}

	public String getIsbn13() {
		return isbn13;
	}

	public String getSourceKey() {
		return sourceKey;
	}

	public String getTitle() {
		return title;
	}

	public String getAuthors() {
		return authors;
	}

	public String getPublisher() {
		return publisher;
	}

	public String getThumbnailUrl() {
		return thumbnailUrl;
	}

	public LocalDate getPublishedAt() {
		return publishedAt;
	}
}
