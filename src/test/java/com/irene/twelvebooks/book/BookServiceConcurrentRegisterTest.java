package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookRegisterRequest;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;

/**
 * BookServiceRaceTest는 리포지토리가 mock이라 "제약 위반 후 재조회"가 성공한다고 믿을 뿐이다.
 * 진짜 트랜잭션 안에서는 flush 실패가 트랜잭션을 rollback-only로 만들 수 있고, 그러면
 * 재조회가 성공하더라도 커밋에서 터진다. 그 차이를 실제 MySQL로 확인한다.
 *
 * <p>경쟁을 스레드로 재현하면 불안정하므로 사전 조회 한 번만 비워 같은 순서를 만든다:
 * 이미 커밋된 행이 있는데 사전 조회가 그것을 놓친 상태 — 늦게 도착한 요청이 겪는 바로 그 상황.
 * 재조회는 현재 트랜잭션에 묶인 EntityManager로 실제 쿼리를 날린다.
 */
class BookServiceConcurrentRegisterTest extends AbstractIntegrationTest {

	private static final String ISBN = "9788960777330";

	@Autowired
	BookService bookService;

	@Autowired
	EntityManager entityManager;

	@MockitoSpyBean
	BookRepository bookRepository;

	@BeforeEach
	void clean() {
		bookRepository.deleteAll();
	}

	@Test
	@DisplayName("먼저 등록된 책이 있으면 제약 위반 뒤에도 그 행을 돌려주고 커밋된다")
	void returnsWinnerRowAfterConstraintViolation() {
		Book winner = bookRepository.saveAndFlush(Book.builder()
				.isbn13(ISBN).title("코드 컴플리트").authors("스티브 맥코넬")
				.publisher("위키북스").publishedAt(LocalDate.of(2017, 5, 10)).build());

		AtomicInteger calls = new AtomicInteger();
		willAnswer(invocation -> calls.getAndIncrement() == 0
				? Optional.empty() // 사전 조회 — 아직 없다고 본다
				: entityManager.createQuery("select b from Book b where b.isbn13 = :isbn", Book.class)
						.setParameter("isbn", ISBN)
						.getResultList().stream().findFirst()) // 제약 위반 후 재조회
				.given(bookRepository).findByIsbn13(ISBN);

		Book result = bookService.upsert(new BookRegisterRequest(ISBN, "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, LocalDate.of(2017, 5, 10)));

		assertThat(result.getId()).isEqualTo(winner.getId());
		assertThat(bookRepository.count()).isEqualTo(1);
	}
}
