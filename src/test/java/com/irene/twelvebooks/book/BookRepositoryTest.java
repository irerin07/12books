package com.irene.twelvebooks.book;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	BookRepository bookRepository;

	@BeforeEach
	void clean() {
		bookRepository.deleteAll();
	}

	private Book withIsbn(String isbn13) {
		return Book.builder()
				.isbn13(isbn13)
				.title("코드 컴플리트")
				.authors("스티브 맥코넬")
				.publisher("위키북스")
				.thumbnailUrl("https://example.com/cover.jpg")
				.publishedAt(LocalDate.of(2017, 5, 10))
				.build();
	}

	@Test
	@DisplayName("ISBN13으로 책을 찾는다")
	void findsByIsbn13() {
		bookRepository.save(withIsbn("9788960777330"));

		assertThat(bookRepository.findByIsbn13("9788960777330"))
				.get()
				.extracting(Book::getTitle)
				.isEqualTo("코드 컴플리트");
	}

	@Test
	@DisplayName("총 쪽수는 비어 있을 수 있다 — 카카오가 주지 않는다")
	void allowsNullPageCount() {
		Book saved = bookRepository.save(withIsbn("9788960777331"));

		assertThat(saved.getPageCount()).isNull();
	}

	@Test
	@DisplayName("같은 ISBN13은 두 번 저장되지 않는다")
	void rejectsDuplicateIsbn13() {
		bookRepository.saveAndFlush(withIsbn("9788960777332"));

		assertThatThrownBy(() -> bookRepository.saveAndFlush(withIsbn("9788960777332")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("ISBN이 없는 책은 sourceKey로 구분한다")
	void findsBySourceKey() {
		bookRepository.saveAndFlush(Book.builder()
				.sourceKey("abc123")
				.title("제목만 있는 책")
				.authors("작자 미상")
				.publisher("출판사")
				.build());

		assertThat(bookRepository.findBySourceKey("abc123")).isPresent();
	}

	@Test
	@DisplayName("같은 sourceKey는 두 번 저장되지 않는다")
	void rejectsDuplicateSourceKey() {
		bookRepository.saveAndFlush(Book.builder()
				.sourceKey("dup-key").title("책1").authors("저자").publisher("출판사").build());

		assertThatThrownBy(() -> bookRepository.saveAndFlush(Book.builder()
				.sourceKey("dup-key").title("책2").authors("저자").publisher("출판사").build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("ISBN이 없는 책이 여럿이어도 저장된다 — MySQL은 NULL 중복을 허용한다")
	void allowsManyRowsWithoutIsbn() {
		bookRepository.saveAndFlush(Book.builder()
				.sourceKey("key-1").title("책1").authors("저자").publisher("출판사").build());
		bookRepository.saveAndFlush(Book.builder()
				.sourceKey("key-2").title("책2").authors("저자").publisher("출판사").build());

		assertThat(bookRepository.count()).isEqualTo(2);
	}
}
