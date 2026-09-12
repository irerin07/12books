package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.reading.dto.ReadingCreateRequest;
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
 * 서재에서 뺀 책을 두 요청이 동시에 다시 담는 경우. 목록에서 담기 버튼을 연달아 누르면
 * 실제로 생긴다.
 *
 * <p>잠금을 잡는 것과 <b>이미 읽은 엔티티를 새로고침하는 것</b>은 다르다. 먼저 평범하게
 * 조회해 엔티티를 영속성 컨텍스트에 올린 뒤 같은 행을 잠금 조회하면, Hibernate는 잠금만 잡고
 * 이미 들고 있던 인스턴스를 그대로 돌려준다. 그러면 앞 요청이 되살려 커밋한 뒤에도 뒤 요청은
 * <b>자기가 처음 읽었을 때의 inBookshelf</b>를 보고 "아직 빠져 있다"고 판단한다.
 *
 * <p>결과는 둘 다 201이고, 뒤 요청이 앞 요청의 상태를 덮어쓴다. 잠금을 잡았는데도 직렬화가
 * 되지 않는 셈이라, 잠금 코드를 읽는 것만으로는 알아채기 어렵다.
 */
class ReadingReshelveConcurrentTest extends AbstractIntegrationTest {

	@Autowired
	ReadingService readingService;

	@Autowired
	ReadingRepository readingRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@RepeatedTest(5)
	@DisplayName("뺀 책을 동시에 다시 담으면 하나만 성공하고 나머지는 409다")
	void concurrentReshelveHappensOnce() throws Exception {
		Long userId = userRepository.save(
				User.create("me@example.com", "hash", "irene", "아이린")).getId();
		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();

		Long readingId = readingRepository.saveAndFlush(
				Reading.of(userId, bookId, ReadingStatus.READING, LocalDateTime.now())).getId();
		// 사용자가 쓰는 경로 그대로 뺀다.
		readingService.remove(userId, readingId);

		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		// 서로 다른 상태로 담는다 — 뒤엣것이 앞엣것을 덮으면 상태로 드러난다.
		List<ReadingStatus> wanted = List.of(ReadingStatus.WANT_TO_READ, ReadingStatus.FINISHED);
		ExecutorService pool = Executors.newFixedThreadPool(wanted.size());
		try {
			for (ReadingStatus status : wanted) {
				pool.submit(() -> {
					try {
						start.await();
						readingService.add(userId, new ReadingCreateRequest(bookId, status, true));
					}
					catch (Throwable e) {
						failures.add(e);
					}
				});
			}
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			pool.shutdownNow();
		}

		// 정확히 하나만 통과한다. 둘 다 통과하면 나중 요청이 먼저 것의 상태를 덮은 것이고,
		// 사용자는 자기가 고른 상태가 아닌 서재를 보게 된다.
		assertThat(failures).hasSize(1);
		assertThat(failures.get(0))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.READING_ALREADY_EXISTS);

		assertThat(readingRepository.count()).isEqualTo(1);
		assertThat(readingRepository.findById(readingId).orElseThrow().isInBookshelf()).isTrue();
	}

	/**
	 * 처음 담는 책을 동시에 두 번 담는 경우.
	 *
	 * <p>사전 조회와 insert 사이는 비어 있어 둘 다 "없다"를 볼 수 있다. 마지막 방어선은
	 * {@code uk(user_id, shelved_book_id)}인데, 그 컬럼은 <b>생성 컬럼</b>이다 —
	 * 꽂혀 있으면 {@code book_id}, 빠져 있으면 NULL. 이 식이 틀리면 제약이 조용히 아무것도
	 * 막지 않고, 같은 책이 서재에 두 줄로 보인다.
	 */
	@RepeatedTest(3)
	@DisplayName("처음 담기를 동시에 두 번 해도 서재에는 한 줄만 남는다")
	void concurrentFirstShelvingKeepsOneRow() throws Exception {
		Long userId = userRepository.save(
				User.create("me@example.com", "hash", "irene", "아이린")).getId();
		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();

		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (int i = 0; i < 2; i++) {
				pool.submit(() -> {
					try {
						start.await();
						readingService.add(userId, new ReadingCreateRequest(bookId, ReadingStatus.READING, null));
					}
					catch (Throwable e) {
						failures.add(e);
					}
				});
			}
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(failures).hasSize(1);
		assertThat(failures.get(0))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.READING_ALREADY_EXISTS);
		assertThat(readingRepository.count()).isEqualTo(1);
	}

	/**
	 * <b>이어서 읽기</b>와 <b>새로 시작</b>이 동시에 들어오는 경우. 사용자가 모달에서 한쪽을
	 * 누르고 응답이 늦어 다른 쪽을 눌렀을 때 생긴다.
	 *
	 * <p>둘 다 "지금 서재에 없음"을 확인한 뒤, 새로 시작은 <b>새 행을 넣고</b> 이어서 읽기는
	 * <b>지난 행을 켠다.</b> 지난 행을 잠가도 새 행 insert는 직렬화되지 않으므로 결국 둘 다
	 * 꽂힌 행이 되려 하고, {@code uk(user_id, shelved_book_id)}에 걸린다.
	 *
	 * <p>제약에 걸리는 것 자체는 맞다 — 한쪽은 실패해야 한다. 문제는 이어서 읽기 쪽의 UPDATE가
	 * 예외를 409로 바꾸는 구간 밖에 있어 <b>500</b>으로 나가는 것이다. 사용자에게는 "서버 오류"고,
	 * 다시 눌러 달라고 안내할 수도 없다.
	 */
	@RepeatedTest(5)
	@DisplayName("이어서 읽기와 새로 시작이 겹쳐도 500이 아니라 409다")
	void concurrentResumeAndFreshStart() throws Exception {
		Long userId = userRepository.save(
				User.create("me@example.com", "hash", "irene", "아이린")).getId();
		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();

		Long readingId = readingRepository.saveAndFlush(
				Reading.of(userId, bookId, ReadingStatus.READING, LocalDateTime.now())).getId();
		readingService.remove(userId, readingId);

		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (Boolean resume : List.of(Boolean.TRUE, Boolean.FALSE)) {
				pool.submit(() -> {
					try {
						start.await();
						readingService.add(userId,
								new ReadingCreateRequest(bookId, ReadingStatus.READING, resume));
					}
					catch (Throwable e) {
						failures.add(e);
					}
				});
			}
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			pool.shutdownNow();
		}

		// 한쪽은 반드시 실패한다. 그 실패가 409여야 한다 — 제약 위반이 그대로 새어 나가면 500이다.
		assertThat(failures).hasSize(1);
		assertThat(failures.get(0))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.READING_ALREADY_EXISTS);

		// 어느 쪽이 이겼든 서재에 꽂힌 행은 하나다.
		assertThat(readingRepository.findShelvedId(userId, bookId)).isPresent();
	}
}
