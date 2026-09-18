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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴가 진행되는 동안 <b>같은 사람이 댓글을 쓰는</b> 경우.
 *
 * <p>탈퇴는 "지금 보이는 내 댓글만큼" 숫자를 내린다. 그 세는 순간과 댓글이 들어오는 순간이
 * 엇갈리면 목록과 숫자가 어긋난다 — 목록에는 없는데 숫자는 하나 더 있거나, 그 반대다.
 *
 * <p>여기서 만드는 순서는 <b>쓰기가 먼저 끝나고 탈퇴가 뒤에 세는 것</b>이다. 탈퇴를 비밀번호
 * 검증 안에서 멈춰 세우고, 그사이 댓글을 끝까지 쓴 뒤 풀어 준다.
 */
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(WithdrawalWriteRaceTest.GatedEncoder.class)
class WithdrawalWriteRaceTest extends AbstractIntegrationTest {

	static class Gate implements PasswordEncoder {

		private final PasswordEncoder delegate = new BCryptPasswordEncoder();

		final CountDownLatch entered = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);

		@Override
		public String encode(CharSequence rawPassword) {
			return delegate.encode(rawPassword);
		}

		@Override
		public boolean matches(CharSequence rawPassword, String encodedPassword) {
			entered.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return delegate.matches(rawPassword, encodedPassword);
		}
	}

	@TestConfiguration
	static class GatedEncoder {

		@Bean
		Gate passwordEncoder() {
			return new Gate();
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

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("탈퇴 도중에 쓴 댓글도 목록과 숫자가 어긋나지 않는다")
	void keepsCountAndListTogether() throws Exception {
		User me = userRepository.save(User.create("me@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "other", "남"));
		String bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		String otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());

		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		Long postId = postRepository.save(
				Post.write(other.getId(), bookId, null, "남이 쓴 글이다.", null, null, false)).getId();
		commentRepository.save(Comment.write(postId, other.getId(), "남는 댓글"));


		// 1. 탈퇴가 비밀번호 검증에서 멈춘다.
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
		assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();

		// 2. 그사이 댓글이 끝까지 들어간다 — 이 시점에는 아직 탈퇴자가 아니다.
		mockMvc.perform(post("/api/v1/posts/{id}/comments", postId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"content": "탈퇴 직전에 쓴 댓글"}"""))
				.andExpect(status().isCreated());

		// 3. 탈퇴가 이어서 센다. 방금 들어온 댓글까지 함께 빠져야 한다.
		gate.release.countDown();
		assertThat(withdrawal.join()).isEqualTo(204);

		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).header("Authorization", otherBearer))
				.andExpect(jsonPath("$.items.length()").value(1));
		mockMvc.perform(get("/api/v1/posts/{id}", postId).header("Authorization", otherBearer))
				.andExpect(jsonPath("$.commentCount").value(1));
	}

	@Autowired
	Gate gate;
}
