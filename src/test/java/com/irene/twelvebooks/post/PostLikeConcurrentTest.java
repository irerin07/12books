package com.irene.twelvebooks.post;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여러 사람이 같은 글에 동시에 좋아요를 누르는 경우.
 *
 * <p>카운터를 읽어서 더한 뒤 쓰면, 동시에 들어온 두 요청이 같은 값을 읽고 같은 값을 써서
 * 하나가 유실된다. 각자는 자기 스냅샷에서 옳지만 합쳐진 결과가 실제 좋아요 행 수와 어긋난다.
 * 그러면 화면의 숫자가 목록과 맞지 않고, 그 차이는 되돌아오지 않는다 — 다음 좋아요가 틀린
 * 값에서 다시 시작하기 때문이다.
 *
 * <p>유니크 제약은 이것을 막지 못한다. 사람이 저마다 다르므로 제약에 걸리는 요청이 없다.
 */
class PostLikeConcurrentTest extends AbstractIntegrationTest {

	private static final int LIKERS = 10;

	@Autowired
	PostLikeService postLikeService;

	@Autowired
	PostRepository postRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@RepeatedTest(3)
	@DisplayName("열 명이 동시에 눌러도 좋아요 수가 정확히 열이다")
	void concurrentLikesAreNotLost() throws Exception {
		Long authorId = userRepository.save(
				User.create("author@example.com", "hash", "irene", "아이린")).getId();
		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		Long postId = postRepository.save(
				Post.write(authorId, bookId, null, "이름 짓기 장.", null, null, false)).getId();

		List<Long> likerIds = new ArrayList<>();
		for (int i = 0; i < LIKERS; i++) {
			likerIds.add(userRepository.save(
					User.create("liker%d@example.com".formatted(i), "hash",
							"liker%d".formatted(i), "독자%d".formatted(i))).getId());
		}

		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService pool = Executors.newFixedThreadPool(LIKERS);
		try {
			for (Long likerId : likerIds) {
				pool.submit(() -> {
					try {
						start.await();
						postLikeService.like(likerId, postId);
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

		// 서로 다른 사람이라 제약에 걸릴 요청이 없다. 여기서 실패가 나오면 원자적 갱신이
		// 아니라 잠금 경합이 문제라는 뜻이고, 그것도 사용자에게는 오류다.
		assertThat(failures).isEmpty();
		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(LIKERS);
	}

	/**
	 * 누르기와 취소가 겹치는 경우.
	 *
	 * <p>서로 <b>다른 사람</b>의 누르기와 취소다. 둘 다 글 행을 먼저 잠그므로 순서가 같고,
	 * 기다릴 뿐 물리지 않는다. 같은 사람일 때의 더 좁은 경우는 아래 테스트가 본다.
	 */
	@RepeatedTest(3)
	@DisplayName("같은 글에 누르기와 취소가 겹쳐도 교착 없이 끝난다")
	void concurrentLikeAndUnlikeDoNotDeadlock() throws Exception {
		Long authorId = userRepository.save(
				User.create("author@example.com", "hash", "irene", "아이린")).getId();
		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		Long postId = postRepository.save(
				Post.write(authorId, bookId, null, "이름 짓기 장.", null, null, false)).getId();

		List<Long> likerIds = new ArrayList<>();
		for (int i = 0; i < LIKERS; i++) {
			likerIds.add(userRepository.save(
					User.create("liker%d@example.com".formatted(i), "hash",
							"liker%d".formatted(i), "독자%d".formatted(i))).getId());
		}

		// 절반은 미리 눌러 둔다. 이들이 취소하는 동안 나머지 절반이 누른다.
		List<Long> cancelling = likerIds.subList(0, LIKERS / 2);
		List<Long> pressing = likerIds.subList(LIKERS / 2, LIKERS);
		cancelling.forEach(id -> postLikeService.like(id, postId));

		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService pool = Executors.newFixedThreadPool(LIKERS);
		try {
			cancelling.forEach(id -> pool.submit(
					() -> run(start, failures, () -> postLikeService.unlike(id, postId))));
			pressing.forEach(id -> pool.submit(
					() -> run(start, failures, () -> postLikeService.like(id, postId))));

			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(failures).isEmpty();
		// 절반이 빠지고 절반이 들어왔으니 그대로다. 숫자가 맞는지보다 교착이 없었는지가 핵심이다.
		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount())
				.isEqualTo(LIKERS / 2);
	}

	/**
	 * <b>같은 사람</b>이 같은 글에 누르기와 취소를 동시에 보내는 경우. 하트를 연달아 두 번
	 * 누르면 실제로 일어난다.
	 *
	 * <p>앞의 테스트는 누르는 사람과 취소하는 사람이 서로 달라서 이것을 잡지 못한다. 같은
	 * 사람이어야 두 요청이 <b>같은 유니크 키</b>를 두고 맞물린다:
	 *
	 * <ol>
	 *   <li>취소가 좋아요 행을 지우고 그 행의 잠금을 쥔다.
	 *   <li>누르기가 글의 카운터를 올리고 글 행의 잠금을 쥔다.
	 *   <li>누르기가 같은 키를 insert하려다 취소가 쥔 행 잠금을 기다린다.
	 *   <li>취소가 카운터를 내리려다 누르기가 쥔 글 잠금을 기다린다.
	 * </ol>
	 *
	 * <p>둘 중 하나가 409로 끝나는 것은 정상이다 — 취소가 먼저 커밋되면 누르기가 성공하고,
	 * 누르기가 먼저면 이미 눌린 상태라 409다. <b>교착만</b>은 안 된다. 사용자에게 500이고,
	 * 다시 눌러 달라고 할 수도 없다.
	 */
	@RepeatedTest(5)
	@DisplayName("같은 사람이 누르기와 취소를 동시에 보내도 교착이 나지 않는다")
	void sameUserLikeAndUnlikeDoNotDeadlock() throws Exception {
		Long authorId = userRepository.save(
				User.create("author@example.com", "hash", "irene", "아이린")).getId();
		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		Long postId = postRepository.save(
				Post.write(authorId, bookId, null, "이름 짓기 장.", null, null, false)).getId();

		List<Long> likerIds = new ArrayList<>();
		for (int i = 0; i < LIKERS; i++) {
			Long likerId = userRepository.save(
					User.create("liker%d@example.com".formatted(i), "hash",
							"liker%d".formatted(i), "독자%d".formatted(i))).getId();
			likerIds.add(likerId);
			// 이미 눌러 둔 상태에서 시작해야 취소가 지울 행이 있다.
			postLikeService.like(likerId, postId);
		}

		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService pool = Executors.newFixedThreadPool(LIKERS * 2);
		try {
			for (Long likerId : likerIds) {
				pool.submit(() -> run(start, failures, () -> postLikeService.unlike(likerId, postId)));
				pool.submit(() -> run(start, failures, () -> postLikeService.like(likerId, postId)));
			}
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			pool.shutdownNow();
		}

		// 이미 눌렀다는 409는 정상적인 결말이다. 교착은 아니다.
		assertThat(failures)
				.as("교착이 나면 안 된다. 409(ALREADY_LIKED)는 정상")
				.allSatisfy(failure -> assertThat(failure)
						.isInstanceOf(BusinessException.class)
						.extracting(e -> ((BusinessException) e).getErrorCode())
						.isEqualTo(ErrorCode.ALREADY_LIKED));
	}

	private void run(CountDownLatch start, List<Throwable> failures, Runnable action) {
		try {
			start.await();
			action.run();
		}
		catch (Throwable e) {
			failures.add(e);
		}
	}
}
