package com.irene.twelvebooks.report;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 동일 댓글에 삭제와 숨김이 겹쳐도 요청과 공개 집계가 정상인지 확인한다. */
class ModerationConcurrentTest extends AbstractIntegrationTest {

	@Autowired
	com.irene.twelvebooks.post.PostReadRepository postReadRepository;

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
			// 대상 외의 댓글 두 개는 계속 보여야 한다.
			Long target = writeComments(postId);

			reportService.reportComment(authorId, target,
					new ReportCreateRequest(ReportReason.ABUSE, null));
			Long reportId = jdbcTemplate.queryForObject(
					"select max(id) from reports", Long.class);

			Map<String, Throwable> failures = collide(new LinkedHashMap<>(Map.of(
					"삭제", () -> commentService.remove(commenterId, target),
					"숨김", () -> reportAdminService.handle(adminId, reportId, ReportStatus.ACTIONED))));

			// 삭제가 <b>이미 없는 댓글</b>을 만나는 것만 정상이다. 예외 종류를 안 보고 넘기면
			// 교착도, 권한·신고 조회 실패도 함께 묻힌다 — 테스트가 아무것도 지키지 않게 된다.
			Throwable deletion = failures.get("삭제");
			if (deletion != null) {
				assertThat(deletion).as("%d번째 라운드의 삭제", round)
						.isInstanceOf(BusinessException.class);
				assertThat(((BusinessException) deletion).getErrorCode())
						.isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
			}
			// 운영자 처리는 어떤 이유로도 실패하면 안 된다. 교착이 여기서 드러난다.
			assertThat(failures.get("숨김")).as("%d번째 라운드의 숨김 처리", round).isNull();

			long visible = commentRepository.findPostPage(postId, null,
					org.springframework.data.domain.PageRequest.ofSize(10)).size();
			// 셋 중 하나가 사라졌으니 답은 2다. "조회 결과와 같다"만 보면 둘 다 함께
			// 틀렸을 때 통과한다.
			assertThat(visible).as("%d번째 라운드의 보이는 댓글", round).isEqualTo(2);
			assertThat(postReadRepository.findDetail(null, postId).orElseThrow().commentCount())
					.as("%d번째 라운드의 댓글 수", round)
					.isEqualTo(2);
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
		return target;
	}

	/** 두 일을 동시에 던지고, 이름별로 터진 예외를 돌려준다. */
	private Map<String, Throwable> collide(Map<String, Runnable> tasks) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		Map<String, Throwable> failures = new ConcurrentHashMap<>();
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		try {
			for (Map.Entry<String, Runnable> task : tasks.entrySet()) {
				pool.submit(() -> {
					try {
						start.await();
						task.getValue().run();
					}
					catch (Throwable t) {
						failures.put(task.getKey(), t);
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

	/**
	 * 인정된 신고 둘을 <b>동시에</b> 기각해도 글이 돌아온다.
	 *
	 * <p>복구는 "다른 인정된 신고가 남아 있는가"를 보고 정한다. 그 판단을 콘텐츠 UPDATE
	 * <b>이전에</b> 별도 SELECT로 하면, 두 요청이 서로 상대의 아직 커밋되지 않은 인정 상태를
	 * 보고 <b>둘 다</b> 복구를 건너뛴다. 결과는 인정된 신고가 하나도 없는데 글은 숨겨진 채다.
	 *
	 * <p>콘텐츠 행의 잠금만으로는 막히지 않는다 — 잠그러 가기 전에 이미 포기하기 때문이다.
	 * 판단이 UPDATE 안으로 들어가야 뒤에 온 쪽이 앞선 커밋을 보고 다시 판정한다.
	 *
	 * <p>순서는 상관없다. 어느 쪽이 마지막이든 <b>마지막 하나가 기각되는 순간</b> 열려야 한다.
	 */
	@Test
	@DisplayName("인정된 신고 둘을 동시에 기각하면 글이 다시 보인다")
	void concurrentRejectionsRestoreContent() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Long postId = postRepository.save(
					Post.write(authorId, bookId, null, "이름 짓기 장.", null, null, false)).getId();

			// 서로 다른 사람이 각각 신고한다 — 유니크 제약이 같은 사람의 중복 신고를 막고,
			// 자기 글은 신고할 수 없으므로 둘 다 작성자가 아니어야 한다.
			Long first = reportPost(commenterId, postId, ReportReason.ABUSE);
			Long second = reportPost(adminId, postId, ReportReason.SPOILER);

			// 둘 다 인정해서 글을 내린다.
			reportAdminService.handle(adminId, first, ReportStatus.ACTIONED);
			reportAdminService.handle(adminId, second, ReportStatus.ACTIONED);
			assertThat(hiddenAt(postId)).as("%d번째 라운드: 내려져 있어야 한다", round).isNotNull();

			Map<String, Throwable> failures = collide(new LinkedHashMap<>(Map.of(
					"첫 기각", () -> reportAdminService.handle(adminId, first, ReportStatus.REJECTED),
					"둘째 기각", () -> reportAdminService.handle(adminId, second, ReportStatus.REJECTED))));

			// 교착이나 예외로 한쪽이 죽으면 그것대로 문제다. 조용히 넘기면 아래 단언이
			// "처리되지 않아서" 통과하는 경우와 구별되지 않는다.
			assertThat(failures).as("%d번째 라운드의 기각 처리", round).isEmpty();

			assertThat(actionedCount(postId)).as("%d번째 라운드의 남은 인정 신고", round).isZero();
			assertThat(hiddenAt(postId))
					.as("%d번째 라운드: 인정된 신고가 없는데 숨겨져 있다", round)
					.isNull();
		}
	}

	private Long reportPost(Long reporterId, Long postId, ReportReason reason) {
		reportService.reportPost(reporterId, postId, new ReportCreateRequest(reason, null));
		return jdbcTemplate.queryForObject("select max(id) from reports", Long.class);
	}

	private Object hiddenAt(Long postId) {
		return jdbcTemplate.queryForObject(
				"select hidden_at from posts where id = ?", Object.class, postId);
	}

	private Integer actionedCount(Long postId) {
		return jdbcTemplate.queryForObject(
				"select count(*) from reports where target_type = 'POST' and target_id = ?"
						+ " and status = 'ACTIONED'", Integer.class, postId);
	}
}
