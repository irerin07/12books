package com.irene.twelvebooks.user;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.post.Comment;
import com.irene.twelvebooks.post.CommentRepository;
import com.irene.twelvebooks.post.Post;
import com.irene.twelvebooks.post.PostRepository;
import com.irene.twelvebooks.report.ReportReason;
import com.irene.twelvebooks.report.ReportService;
import com.irene.twelvebooks.report.ReportStatus;
import com.irene.twelvebooks.report.ReportAdminService;
import com.irene.twelvebooks.report.dto.ReportCreateRequest;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 탈퇴한 작성자의 댓글은 운영자가 숨기거나 복구해도 공개 집계에서 제외한다. */
class WithdrawalModerationOverlapTest extends AbstractIntegrationTest {

	@Autowired
	com.irene.twelvebooks.post.PostReadRepository postReadRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	PostRepository postRepository;

	@Autowired
	CommentRepository commentRepository;

	@Autowired
	ReportService reportService;

	@Autowired
	ReportAdminService reportAdminService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("탈퇴 후 숨김과 복구에도 공개 댓글 수는 0이다")
	void keepsCountCorrectWhenModerationInterleaves() {
		User me = userRepository.save(User.create("me@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "other", "남"));
		User admin = userRepository.save(User.create("admin@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "admin", "운영자"));
		jdbcTemplate.update("update users set role = 'ADMIN' where id = ?", admin.getId());

		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		Long postId = postRepository.save(
				Post.write(other.getId(), bookId, null, "남이 쓴 글이다.", null, null, false)).getId();
		Long commentId = commentRepository.save(
				Comment.write(postId, me.getId(), "탈퇴할 사람의 댓글")).getId();

		reportService.reportComment(other.getId(), commentId,
				new ReportCreateRequest(ReportReason.ABUSE, null));
		Long reportId = jdbcTemplate.queryForObject("select max(id) from reports", Long.class);

		assertThat(postReadRepository.findDetail(null, postId).orElseThrow().commentCount()).isEqualTo(1);

		// 1. 탈퇴 표시가 먼저 커밋된다.
		assertThat(userRepository.withdraw(me.getId(), LocalDateTime.now())).isEqualTo(1);

		// 2. 탈퇴 후 운영자가 댓글을 숨긴다.
		reportAdminService.handle(admin.getId(), reportId, ReportStatus.ACTIONED, null);
		assertThat(postReadRepository.findDetail(null, postId).orElseThrow().commentCount()).isZero();

		// 복구해도 탈퇴자의 댓글은 공개되지 않는다.

		reportAdminService.handle(admin.getId(), reportId, ReportStatus.REJECTED, true);

		// 보이는 댓글은 0개다. 숫자도 0이어야 한다.
		assertThat(commentRepository.findPostPage(postId, null,
				org.springframework.data.domain.PageRequest.ofSize(10))).isEmpty();
		assertThat(postReadRepository.findDetail(null, postId).orElseThrow().commentCount()).isZero();
	}
}
