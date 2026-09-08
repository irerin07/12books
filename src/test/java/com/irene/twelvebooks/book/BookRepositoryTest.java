package com.irene.twelvebooks.book;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	BookRepository bookRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

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

	/**
	 * Builder도 같은 불변식을 막지만 그것은 JPA를 지나는 경로에서만 유효하다.
	 * 배치·JDBC처럼 엔티티를 거치지 않는 쓰기가 생겨도 스키마가 마지막으로 막아야 한다.
	 */
	private void insertDirectly(String isbn13, String sourceKey) {
		jdbcTemplate.update("""
				insert into books (isbn13, source_key, title, authors, created_at, updated_at)
				values (?, ?, ?, ?, now(6), now(6))""", isbn13, sourceKey, "제목", "저자");
	}

	/*
	 * CHECK 위반은 MySQL 오류 3819로 오는데 Spring의 오류 코드 표에 없어 무결성 위반이 아니라
	 * UncategorizedSQLException으로 번역된다. 여기서 확인하려는 것은 예외의 종류가 아니라
	 * "DB가 이 제약으로 거부했다"는 사실이므로 상위 타입과 제약 이름으로 본다.
	 */
	@Test
	@DisplayName("두 키가 다 있는 행은 DB가 거부한다")
	void rejectsRowWithBothKeys() {
		assertThatThrownBy(() -> insertDirectly("9788960777340", "key-both"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("ck_books_identity");
	}

	@Test
	@DisplayName("두 키가 다 없는 행은 DB가 거부한다 — 어느 쪽으로도 유일성이 지켜지지 않는다")
	void rejectsRowWithNeitherKey() {
		assertThatThrownBy(() -> insertDirectly(null, null))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("ck_books_identity");
	}

	@Test
	@DisplayName("키가 하나만 있는 행은 들어간다")
	void acceptsRowWithExactlyOneKey() {
		insertDirectly("9788960777341", null);
		insertDirectly(null, "key-only");

		assertThat(bookRepository.count()).isEqualTo(2);
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
