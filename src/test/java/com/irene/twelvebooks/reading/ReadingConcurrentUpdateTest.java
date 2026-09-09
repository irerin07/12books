package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.reading.dto.ReadingUpdateRequest;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서로 얽힌 필드를 동시에 고치는 경우.
 *
 * <p>{@code status}와 {@code currentPage}는 독립 필드가 아니다 — "완독이면서 총 쪽수를 알면
 * 진도는 끝"이라는 규칙이 둘을 묶는다. 한쪽이 완독으로 바꾸고 다른 쪽이 진도를 중간으로
 * 옮기면, 각자는 자기 스냅샷에서 옳지만 합쳐진 결과가 규칙을 깬다. 컬럼 단위 부분 UPDATE는
 * 이것을 막지 못한다(각 컬럼의 마지막 쓰기가 그대로 남는다).
 *
 * <p>DB의 CHECK도 못 막는다. {@code current_page <= page_count}는 150 ≤ 300이라 통과한다.
 */
class ReadingConcurrentUpdateTest extends AbstractIntegrationTest {

	@Autowired
	ReadingService readingService;

	@Autowired
	ReadingRepository readingRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@RepeatedTest(5)
	@DisplayName("완독 전환과 진도 수정이 겹쳐도 완독한 책의 진도는 끝에 있다")
	void concurrentFinishAndProgressKeepTheInvariant() throws Exception {
		Long userId = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린")).getId();
		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();

		Reading seed = Reading.of(userId, bookId, ReadingStatus.READING, LocalDateTime.now());
		seed.applyProgress(300, 100);
		Long readingId = readingRepository.save(seed).getId();

		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			pool.submit(() -> attempt(start, failures, userId, readingId,
					new ReadingUpdateRequest(ReadingStatus.FINISHED, null, null, null)));
			pool.submit(() -> attempt(start, failures, userId, readingId,
					new ReadingUpdateRequest(null, 150, null, null)));

			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			pool.shutdownNow();
		}

		// 어느 쪽도 실패하면 안 된다. 잠금은 뒤에 온 요청을 기다리게 할 뿐 거절하지 않는다.
		// 이것을 보지 않으면 둘 다 실패해 READING으로 남은 경우까지 통과해버린다.
		assertThat(failures).isEmpty();

		Reading result = readingRepository.findById(readingId).orElseThrow();
		// 어느 순서로 겹쳤든 결론은 하나다 — 완독한 책의 진도는 끝에 있다.
		assertThat(result.getStatus()).isEqualTo(ReadingStatus.FINISHED);
		assertThat(result.getCurrentPage()).isEqualTo(300);
	}

	private void attempt(CountDownLatch start, List<Throwable> failures, Long userId, Long readingId,
			ReadingUpdateRequest request) {
		try {
			start.await();
			readingService.update(userId, readingId, request);
		}
		catch (Throwable e) {
			failures.add(e);
		}
	}
}
