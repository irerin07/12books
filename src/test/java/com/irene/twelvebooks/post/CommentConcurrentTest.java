package com.irene.twelvebooks.post;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.post.dto.CommentCreateRequest;
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
 * 여러 사람이 같은 글에 동시에 댓글을 다는 경우.
 *
 * <p>좋아요와 같은 함정이 있다. {@code comments} insert는 외래 키 때문에 부모인 {@code posts}
 * 행에 <b>공유</b> 잠금을 잡는데, 이어지는 {@code commentCount} UPDATE가 같은 행에 <b>배타</b>
 * 잠금을 요구한다. 동시에 들어온 요청들이 서로 공유 잠금을 쥔 채 상대의 배타 잠금을 기다리면
 * MySQL이 한쪽을 죽이고, 사용자는 500을 받는다.
 *
 * <p>좋아요 쪽만 고치고 여기를 놓치면 같은 버그가 다른 경로로 남는다.
 */
class CommentConcurrentTest extends AbstractIntegrationTest {

	private static final int WRITERS = 10;

	@Autowired
	CommentService commentService;

	@Autowired
	PostRepository postRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@RepeatedTest(3)
	@DisplayName("열 명이 같은 글에 동시에 댓글을 달아도 교착 없이 열 개가 남는다")
	void concurrentCommentsAreNotLost() throws Exception {
		Long authorId = userRepository.save(
				User.create("author@example.com", "hash", "irene", "아이린")).getId();
		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		Long postId = postRepository.save(
				Post.write(authorId, bookId, null, "이름 짓기 장.", null, null, false)).getId();

		List<Long> writerIds = new ArrayList<>();
		for (int i = 0; i < WRITERS; i++) {
			writerIds.add(userRepository.save(
					User.create("writer%d@example.com".formatted(i), "hash",
							"writer%d".formatted(i), "독자%d".formatted(i))).getId());
		}

		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
		try {
			for (Long writerId : writerIds) {
				pool.submit(() -> {
					try {
						start.await();
						commentService.write(writerId, postId,
								new CommentCreateRequest("저도 그 장에서 멈췄어요."));
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

		// 서로 다른 사람이 각자 다른 댓글을 다는 것뿐이라 실패할 이유가 없다.
		assertThat(failures).isEmpty();
		assertThat(postRepository.findById(postId).orElseThrow().getCommentCount()).isEqualTo(WRITERS);
	}

	/**
	 * 댓글 작성자와 글 작성자가 <b>같은 댓글</b>을 동시에 지우는 경우. 둘 다 지울 권한이
	 * 있으므로 실제로 일어난다.
	 *
	 * <p>조회한 뒤 엔티티를 지우면, 두 요청이 같은 행을 함께 읽고 차례로 지우게 된다.
	 * 뒤엣것의 DELETE가 0행을 만나 Hibernate가 "예상 1행, 실제 0행"으로 예외를 던지고
	 * 사용자에게는 500이 된다 — 언팔로우에서 이미 한 번 겪은 모양이다.
	 *
	 * <p>지우려던 댓글이 사라졌다는 결말은 두 요청 모두가 원한 것이다. 그래서 둘 다 성공이고,
	 * 카운터는 <b>실제로 지운 쪽에서만</b> 내려가 정확히 하나가 빠진다.
	 */
	@RepeatedTest(5)
	@DisplayName("같은 댓글을 동시에 지워도 둘 다 성공하고 카운터는 하나만 빠진다")
	void concurrentDeleteOfSameComment() throws Exception {
		Long postAuthorId = userRepository.save(
				User.create("author@example.com", "hash", "irene", "아이린")).getId();
		Long commentAuthorId = userRepository.save(
				User.create("other@example.com", "hash", "other", "남")).getId();
		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		Long postId = postRepository.save(
				Post.write(postAuthorId, bookId, null, "이름 짓기 장.", null, null, false)).getId();

		Long commentId = commentService.write(commentAuthorId, postId,
				new CommentCreateRequest("저도 그 장에서 멈췄어요.")).id();

		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			// 댓글 작성자와 글 작성자가 동시에 누른다. 둘 다 권한이 있다.
			for (Long remover : List.of(commentAuthorId, postAuthorId)) {
				pool.submit(() -> {
					try {
						start.await();
						commentService.remove(remover, commentId);
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

		assertThat(failures).isEmpty();
		// 한 번만 빠져야 한다. 둘 다 내리면 음수가 되고, 아무도 안 내리면 지운 댓글이 남는다.
		assertThat(postRepository.findById(postId).orElseThrow().getCommentCount()).isZero();
	}
}
