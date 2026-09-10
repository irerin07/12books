package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;

/**
 * 같은 사람이 같은 책에 감상평 둘을 동시에 올리면 두 요청이 함께 "서재에 없음"을 본다.
 * 늦게 도착한 쪽은 유니크 제약에 걸리는데, 그때 사용자에게 오류를 보여줄 일이 아니라
 * 먼저 만들어진 기록에 붙기만 하면 된다.
 *
 * <p>경쟁을 스레드로 재현하면 불안정하므로 사전 조회 한 번만 비워 같은 순서를 만든다 —
 * {@code BookServiceConcurrentRegisterTest}와 같은 방식이다.
 */
class ReadingLinkerConcurrentTest extends AbstractIntegrationTest {

	@Autowired
	ReadingLinker readingLinker;

	@Autowired
	EntityManager entityManager;

	@MockitoSpyBean
	ReadingRepository readingRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	private Long userId;
	private Long bookId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린")).getId();
		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
	}

	@Test
	@DisplayName("먼저 만들어진 서재 기록이 있으면 제약 위반 뒤에도 그 기록에 붙는다")
	void reusesWinnerRowAfterConstraintViolation() {
		Reading winner = readingRepository.saveAndFlush(
				Reading.of(userId, bookId, ReadingStatus.READING, LocalDateTime.now()));

		AtomicInteger calls = new AtomicInteger();
		willAnswer(invocation -> calls.getAndIncrement() == 0
				? Optional.empty() // 사전 조회 — 아직 없다고 본다
				: entityManager.createQuery(
								"select r from Reading r where r.userId = :userId and r.bookId = :bookId",
								Reading.class)
						.setParameter("userId", userId).setParameter("bookId", bookId)
						.getResultList().stream().findFirst()) // 제약 위반 후 재조회
				.given(readingRepository).findByUserIdAndBookId(userId, bookId);

		Reading linked = readingLinker.linkOrCreate(userId, bookId);

		assertThat(linked.getId()).isEqualTo(winner.getId());
		assertThat(readingRepository.count()).isEqualTo(1);
	}
}
