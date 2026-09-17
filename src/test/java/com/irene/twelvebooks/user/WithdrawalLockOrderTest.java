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

/** 탈퇴는 다른 사용자의 게시글 잠금을 기다리지 않는다. */
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
		// 탈퇴자의 기존 댓글이 있는 게시글을 잠근다.
		commentRepository.save(Comment.write(postId, me.getId(), "먼저 단 댓글"));

		try (Connection writer = dataSource.getConnection()) {
			writer.setAutoCommit(false);

			// 1. 별도 트랜잭션이 게시글 행을 잠근다.
			try (PreparedStatement counter = writer.prepareStatement(
					"update posts set like_count = like_count where id = ?")) {
				counter.setLong(1, postId);
				counter.executeUpdate();
			}

			// 2. 게시글 잠금이 풀리기 전에도 탈퇴가 완료되어야 한다.
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
			assertThat(withdrawal.get(20, TimeUnit.SECONDS)).isEqualTo(204);

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
						"select count(*) from comments c join users u on u.id = c.author_id where c.post_id = ? and c.hidden_at is null and c.deleted_at is null and u.deleted_at is null")) {
			query.setLong(1, postId);
			try (var rows = query.executeQuery()) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getInt(1)).isZero();
			}
		}
	}
}
