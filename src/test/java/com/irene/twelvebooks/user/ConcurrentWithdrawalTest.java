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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * 탈퇴 요청이 두 번 겹치는 경우. (기기 둘에서 동시에 누르거나, 응답이 안 와서 다시 누르거나.)
 *
 * <p>두 요청이 탈퇴 전 상태를 함께 읽으면 <b>댓글 수를 두 번 깎는다.</b> 댓글 행 자체는
 * 바뀌지 않으므로 두 번째도 같은 댓글을 세기 때문이다 — 실제로 보이는 댓글은 하나인데
 * 숫자는 0이 된다.
 *
 * <p>순서를 시간이 아니라 <b>관문</b>으로 만든다. 비밀번호 검증 안에서 두 요청이 서로를
 * 기다리게 해, 둘 다 탈퇴 전 상태를 들고 통과하도록 강제한다.
 */
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(ConcurrentWithdrawalTest.PairedEncoder.class)
class ConcurrentWithdrawalTest extends AbstractIntegrationTest {

	/** 두 요청이 다 도착할 때까지 서로를 기다리는 인코더. */
	static class Paired implements PasswordEncoder {

		private final PasswordEncoder delegate = new BCryptPasswordEncoder();

		final CyclicBarrier gate = new CyclicBarrier(2);

		@Override
		public String encode(CharSequence rawPassword) {
			return delegate.encode(rawPassword);
		}

		@Override
		public boolean matches(CharSequence rawPassword, String encodedPassword) {
			try {
				gate.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException | BrokenBarrierException | java.util.concurrent.TimeoutException e) {
				Thread.currentThread().interrupt();
			}
			return delegate.matches(rawPassword, encodedPassword);
		}
	}

	@TestConfiguration
	static class PairedEncoder {

		@Bean
		Paired passwordEncoder() {
			return new Paired();
		}
	}

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

	@Test
	@DisplayName("탈퇴 요청이 겹쳐도 댓글 수는 한 번만 줄어든다")
	void withdrawsOnlyOnce() throws Exception {
		User me = userRepository.save(User.create("me@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "other", "남"));
		String bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());

		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		Long postId = postRepository.save(
				Post.write(other.getId(), bookId, null, "남이 쓴 글이다.", null, null, false)).getId();
		commentRepository.save(Comment.write(postId, me.getId(), "탈퇴할 사람의 댓글"));
		commentRepository.save(Comment.write(postId, other.getId(), "남는 댓글"));
		postRepository.findById(postId).orElseThrow();
		jdbcUpdateCommentCount(postId);

		List<CompletableFuture<Integer>> requests = List.of(withdraw(bearer), withdraw(bearer));
		requests.forEach(CompletableFuture::join);

		// 보이는 댓글은 하나(남의 것)다. 두 번 깎이면 0이 된다.
		assertThat(postRepository.findById(postId).orElseThrow().getCommentCount()).isEqualTo(1);
		// 한쪽만 성공하든 둘 다 204든 상관없다 — 결과가 같으면 된다.
		assertThat(requests.stream().map(CompletableFuture::join).filter(status -> status == 204).count())
				.isPositive();
	}

	private CompletableFuture<Integer> withdraw(String bearer) {
		return CompletableFuture.supplyAsync(() -> {
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
	}

	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	/** 댓글을 리포지토리로 직접 넣었으므로 카운터를 실제 개수에 맞춰 둔다. */
	private void jdbcUpdateCommentCount(Long postId) {
		jdbcTemplate.update("update posts set comment_count = 2 where id = ?", postId);
	}
}
