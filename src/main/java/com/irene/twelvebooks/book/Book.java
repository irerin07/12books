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

	/** 카카오가 총 쪽수를 주지 않는다. 사용자가 나중에 입력한다. */
	@Column(name = "page_count")
	private Integer pageCount;

	@Column(name = "published_at")
	private LocalDate publishedAt;

	protected Book() {
	}

	private Book(Builder builder) {
		this.isbn13 = builder.isbn13;
		this.sourceKey = builder.sourceKey;
		this.title = builder.title;
		this.authors = builder.authors;
		this.publisher = builder.publisher;
		this.thumbnailUrl = builder.thumbnailUrl;
		this.pageCount = builder.pageCount;
		this.publishedAt = builder.publishedAt;
	}

	public static Builder builder() {
		return new Builder();
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

	public Integer getPageCount() {
		return pageCount;
	}

	public LocalDate getPublishedAt() {
		return publishedAt;
	}

	/** 필드가 여덟 개라 생성자 인자 순서를 바꿔 넣는 실수가 조용히 통과한다. */
	public static final class Builder {

		private String isbn13;
		private String sourceKey;
		private String title;
		private String authors;
		private String publisher;
		private String thumbnailUrl;
		private Integer pageCount;
		private LocalDate publishedAt;

		public Builder isbn13(String isbn13) {
			this.isbn13 = isbn13;
			return this;
		}

		public Builder sourceKey(String sourceKey) {
			this.sourceKey = sourceKey;
			return this;
		}

		public Builder title(String title) {
			this.title = title;
			return this;
		}

		public Builder authors(String authors) {
			this.authors = authors;
			return this;
		}

		public Builder publisher(String publisher) {
			this.publisher = publisher;
			return this;
		}

		public Builder thumbnailUrl(String thumbnailUrl) {
			this.thumbnailUrl = thumbnailUrl;
			return this;
		}

		public Builder pageCount(Integer pageCount) {
			this.pageCount = pageCount;
			return this;
		}

		public Builder publishedAt(LocalDate publishedAt) {
			this.publishedAt = publishedAt;
			return this;
		}

		public Book build() {
			return new Book(this);
		}
	}
}
