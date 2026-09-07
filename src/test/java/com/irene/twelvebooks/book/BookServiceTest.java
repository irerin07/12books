package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookRegisterRequest;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BookServiceTest extends AbstractIntegrationTest {

	@Autowired
	BookService bookService;

	@Autowired
	BookRepository bookRepository;

	@BeforeEach
	void clean() {
		bookRepository.deleteAll();
	}

	private BookRegisterRequest request(String isbn13) {
		return new BookRegisterRequest(isbn13, "코드 컴플리트", "스티브 맥코넬", "위키북스",
				"https://example.com/c.jpg", LocalDate.of(2017, 5, 10));
	}

	@Test
	@DisplayName("처음 등록하면 새 책이 생긴다")
	void registersNewBook() {
		Book book = bookService.upsert(request("9788960777330"));

		assertThat(book.getId()).isNotNull();
		assertThat(book.getIsbn13()).isEqualTo("9788960777330");
		assertThat(bookRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 ISBN13을 다시 등록해도 새 행이 생기지 않고 같은 id를 돌려준다")
	void reusesExistingBookByIsbn13() {
		Long first = bookService.upsert(request("9788960777330")).getId();
		Long second = bookService.upsert(request("9788960777330")).getId();

		assertThat(second).isEqualTo(first);
		assertThat(bookRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("ISBN이 없으면 제목·저자·출판사 해시를 sourceKey로 쓴다")
	void fallsBackToSourceKey() {
		Book book = bookService.upsert(request(null));

		assertThat(book.getIsbn13()).isNull();
		assertThat(book.getSourceKey()).isNotBlank();
	}

	@Test
	@DisplayName("ISBN이 없는 같은 책을 다시 등록해도 같은 id를 돌려준다")
	void reusesExistingBookBySourceKey() {
		Long first = bookService.upsert(request(null)).getId();
		Long second = bookService.upsert(request(null)).getId();

		assertThat(second).isEqualTo(first);
		assertThat(bookRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("제목이 다르면 다른 책이다")
	void differentTitleIsDifferentBook() {
		bookService.upsert(request(null));
		bookService.upsert(new BookRegisterRequest(null, "다른 책", "스티브 맥코넬", "위키북스", null, null));

		assertThat(bookRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("id로 책을 조회한다")
	void readsById() {
		Long id = bookService.upsert(request("9788960777330")).getId();

		assertThat(bookService.getById(id).getTitle()).isEqualTo("코드 컴플리트");
	}
}
