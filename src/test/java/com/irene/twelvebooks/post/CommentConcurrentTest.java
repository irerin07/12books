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
}
