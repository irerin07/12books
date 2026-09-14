package com.irene.twelvebooks.report;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.post.Comment;
import com.irene.twelvebooks.post.CommentRepository;
import com.irene.twelvebooks.post.CommentService;
import com.irene.twelvebooks.post.Post;
import com.irene.twelvebooks.post.PostRepository;
import com.irene.twelvebooks.report.dto.ReportCreateRequest;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작성자의 삭제와 운영자의 숨김이 같은 댓글에 동시에 닿는 경우.
 *
 * <p>둘 다 <b>댓글을 안 보이게 하고 댓글 수를 하나 줄이는</b> 일을 한다. 그래서 두 가지가 걸린다.
 *
 * <ul>
 *   <li><b>잠금 순서</b> — 한쪽이 글→댓글, 다른 쪽이 댓글→글이면 서로 상대의 잠금을 기다려
 *       MySQL이 한쪽을 죽인다. 사용자는 500을 받는다.</li>
 *   <li><b>이중 차감</b> — 삭제가 "보이는 댓글"을 읽은 뒤 숨김이 먼저 끝나면, 삭제는 그것을
 *       모른 채 한 번 더 줄인다. 댓글 하나가 사라졌는데 숫자는 둘이 줄어든다.</li>
 * </ul>
 *
 * <p>그래서 이 테스트는 <b>예외가 없었는지</b>와 <b>숫자가 실제 보이는 댓글 수와 같은지</b>를
 * 함께 본다. 앞만 보면 숫자가 어긋난 채로 통과하고, 뒤만 보면 교착으로 죽은 요청을 놓친다.
 */
class ModerationConcurrentTest extends AbstractIntegrationTest {

	private static final int ROUNDS = 10;

	@Autowired
	CommentService commentService;

	@Autowired
	ReportService reportService;

	@Autowired
	ReportAdminService reportAdminService;

	@Autowired
	PostRepository postRepository;

	@Autowired
	CommentRepository commentRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private Long authorId;
	private Long commenterId;
	private Long adminId;
	private Long bookId;

	@BeforeEach
	void setUp() {
		authorId = userRepository.save(User.create("author@example.com", "hash", "irene", "아이린")).getId();
		commenterId = userRepository.save(User.create("c@example.com", "hash", "commenter", "댓글이")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "hash", "admin", "운영자")).getId();
		jdbcTemplate.update("update users set role = 'ADMIN' where id = ?", adminId);
		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
	}

	@Test
	@DisplayName("삭제와 숨김이 부딪혀도 교착 없이 댓글 수가 실제와 맞는다")
	void deletionAndHidingDoNotCollide() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Long postId = postRepository.save(
					Post.write(authorId, bookId, null, "이름 짓기 장.", null, null, false)).getId();
			// 세 개를 달아 두면 하나가 사라진 뒤의 정답이 2다. 하나만 두면 카운터의
			// "0 아래로 내려가지 않는다" 방어에 가려 이중 차감이 보이지 않는다.
			Long target = writeComments(postId);

			reportService.reportComment(authorId, target,
					new ReportCreateRequest(ReportReason.ABUSE, null));
			Long reportId = jdbcTemplate.queryForObject(
					"select max(id) from reports", Long.class);

			List<Throwable> failures = collide(
					() -> commentService.remove(commenterId, target),
					() -> reportAdminService.handle(adminId, reportId, ReportStatus.ACTIONED));

			// 교착으로 죽은 요청이 있으면 여기서 드러난다. 한쪽이 "이미 없는 댓글"을 만나는
			// 것은 정상이므로 그것만 걸러 낸다.
			assertThat(failures.stream().filter(t -> !(t instanceof BusinessException)).toList())
					.isEmpty();

			long visible = commentRepository.findPostPage(postId, null,
					org.springframework.data.domain.PageRequest.ofSize(10)).size();
			assertThat(postRepository.findById(postId).orElseThrow().getCommentCount())
					.as("%d번째 라운드의 댓글 수", round)
					.isEqualTo((int) visible);
		}
	}

	/** 댓글 셋을 달고 그중 하나(가운데)를 돌려준다. */
	private Long writeComments(Long postId) {
		Long target = null;
		for (int i = 0; i < 3; i++) {
			Comment comment = commentRepository.save(Comment.write(postId, commenterId, "댓글 " + i));
			if (i == 1) {
				target = comment.getId();
			}
		}
		jdbcTemplate.update("update posts set comment_count = 3 where id = ?", postId);
		return target;
	}

	private List<Throwable> collide(Runnable first, Runnable second) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = new CopyOnWriteArrayList<>();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (Runnable task : List.of(first, second)) {
				pool.submit(() -> {
					try {
						start.await();
						task.run();
					}
					catch (Throwable t) {
						failures.add(t);
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
		return failures;
	}
}
