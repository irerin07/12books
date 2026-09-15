package com.irene.twelvebooks.user;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.post.Comment;
import com.irene.twelvebooks.post.CommentRepository;
import com.irene.twelvebooks.post.Post;
import com.irene.twelvebooks.post.PostRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * 탈퇴와 댓글 작성의 <b>잠금 순서</b>.
 *
 * <p>두 경로가 같은 두 행을 반대 순서로 잡으면 교착이 난다. MySQL이 한쪽을 죽이고, 그 요청은
 * 500으로 끝난다.
 *
 * <ul>
 *   <li>댓글 작성: {@code posts}(카운터 UPDATE) → {@code users}(INSERT의 외래 키가 잡는 공유 잠금)</li>
 *   <li>탈퇴: {@code users}(표시) → {@code posts}(댓글 수 조정)</li>
 * </ul>
 *
 * <p>여기서는 댓글 작성의 두 단계 <b>사이</b>를 벌려야 해서 애플리케이션 경로 대신 같은 순서로
 * 잠그는 SQL을 직접 쓴다 — 서비스 안에는 걸쇠를 걸 이음매가 없다.
 */
class WithdrawalLockOrderTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	PostRepository postRepository;

	@Autowired
	CommentRepository commentRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	DataSource dataSource;

	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("탈퇴와 댓글 작성이 부딪혀도 교착으로 죽지 않는다")
	void doesNotDeadlockWithCommentWrites() throws Exception {
		User me = userRepository.save(User.create("me@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "other", "남"));
		String bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());

		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		Long postId = postRepository.save(
				Post.write(other.getId(), bookId, null, "남이 쓴 글이다.", null, null, false)).getId();
		// 탈퇴가 이 글을 만지게 하려면 그 사람의 댓글이 이미 하나 있어야 한다.
		// 리포지토리로 직접 넣으므로 카운터도 손으로 맞춘다 — 안 맞추면 탈퇴가 숫자를
		// 음수로 내려 CHECK에 걸린다(교착이 아니라 준비가 틀린 것이다).
		commentRepository.save(Comment.write(postId, me.getId(), "먼저 단 댓글"));
		jdbcTemplate.update("update posts set comment_count = 1 where id = ?", postId);

		try (Connection writer = dataSource.getConnection()) {
			writer.setAutoCommit(false);

			// 1. 댓글 작성이 글 행을 잡는다(카운터 UPDATE).
			try (PreparedStatement counter = writer.prepareStatement(
					"update posts set comment_count = comment_count + 1 where id = ?")) {
				counter.setLong(1, postId);
				counter.executeUpdate();
			}

			// 2. 그 상태에서 탈퇴가 시작된다 — 사용자 행을 잡고 글 행을 기다린다.
			CompletableFuture<Integer> withdrawal = CompletableFuture.supplyAsync(() -> {
				try {
					return mockMvc.perform(delete("/api/v1/me").header("Authorization", bearer)
									.contentType(MediaType.APPLICATION_JSON)
									.content("""
											{"password": "123456789"}"""))
							.andReturn().getResponse().getStatus();
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			});
			Thread.sleep(1500);

			// 3. 이제 댓글을 넣는다. 외래 키가 사용자 행의 공유 잠금을 요구한다 —
			//    순서가 반대면 여기서 서로를 기다린다.
			try (PreparedStatement insert = writer.prepareStatement("""
					insert into comments (post_id, author_id, content, created_at, updated_at)
					values (?, ?, '나중에 단 댓글', now(6), now(6))
					""")) {
				insert.setLong(1, postId);
				insert.setLong(2, me.getId());
				insert.executeUpdate();
			}
			writer.commit();

			// 교착이 났으면 둘 중 하나가 죽는다. 탈퇴는 500이 되고, 쓰기는 위에서 예외가 난다.
			assertThat(withdrawal.get(20, TimeUnit.SECONDS)).isEqualTo(204);
		}

		// 둘 다 끝난 뒤 숫자는 실제와 맞아야 한다 — 탈퇴자의 댓글 둘이 모두 빠진다.
		try (Connection reader = dataSource.getConnection();
				PreparedStatement query = reader.prepareStatement(
						"select comment_count from posts where id = ?")) {
			query.setLong(1, postId);
			try (var rows = query.executeQuery()) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getInt(1)).isZero();
			}
		}
	}
}
