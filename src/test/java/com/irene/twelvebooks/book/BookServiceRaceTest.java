package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookRegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 사전 조회를 함께 통과한 두 요청 중 늦은 쪽이 유니크 제약에 걸리는 상황.
 * 락 없이, 실패를 신호로 삼아 기존 행을 돌려주는 것이 설계다.
 */
class BookServiceRaceTest {

	private final BookRepository bookRepository = mock(BookRepository.class);
	private final BookService bookService = new BookService(bookRepository);

	@Test
	@DisplayName("동시 등록으로 insert가 실패하면 다시 조회해 기존 책을 돌려준다")
	void recoversFromConcurrentInsert() {
		BookRegisterRequest request = new BookRegisterRequest("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, LocalDate.of(2017, 5, 10));
		Book winner = Book.builder().isbn13("9788960777330").title("코드 컴플리트")
				.authors("스티브 맥코넬").publisher("위키북스").build();

		given(bookRepository.findByIsbn13("9788960777330"))
				.willReturn(Optional.empty())    // 사전 조회 — 아직 없다
				.willReturn(Optional.of(winner)); // 제약 위반 후 재조회 — 먼저 넣은 쪽이 보인다
		given(bookRepository.saveAndFlush(any(Book.class)))
				.willThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_books_isbn13'"));

		assertThat(bookService.upsert(request)).isSameAs(winner);
	}
}
