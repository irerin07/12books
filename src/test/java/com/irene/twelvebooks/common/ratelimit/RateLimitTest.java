package com.irene.twelvebooks.common.ratelimit;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 같은 쪽에서 너무 자주 부르는 것을 막는다.
 *
 * <p>지금까지 이 API는 완전히 무방비였다. 누가 스크립트로 가입이나 글쓰기를 돌리면 막을
 * 수단이 없었고, 다른 출시 항목과 달리 <b>터진 뒤에 대응할 시간이 없는</b> 자리다.
 *
 * <p>세는 단위가 둘인 것이 핵심이다. 로그인·가입은 아직 누구인지 모르니 <b>IP</b>로 세고,
 * 로그인한 뒤의 쓰기는 <b>사람</b>으로 센다. 뒤엣것을 IP로 세면 같은 사무실·같은 통신사
 * 뒤에 있는 사람들이 서로의 몫을 깎아먹는다.
 */
class RateLimitTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;
	private String otherBearer;
	private Long bookId;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());
		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
	}

	private MvcResult login(String ip) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").with(request -> {
					request.setRemoteAddr(ip);
					return request;
				})
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"me@example.com","password":"틀린비밀번호"}"""))
				.andReturn();
	}

	@Test
	@DisplayName("로그인을 계속 두드리면 429와 함께 언제 다시 오라고 알려준다")
	void throttlesLoginAttempts() throws Exception {
		int blocked = 0;
		String retryAfter = null;
		for (int attempt = 0; attempt < 40; attempt++) {
			MvcResult result = login("203.0.113.9");
			if (result.getResponse().getStatus() == 429) {
				blocked++;
				retryAfter = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
			}
		}

		assertThat(blocked).isPositive();
		// 몇 초 뒤에 오라는 말이 없으면 클라이언트가 계속 두드린다 — 막는 의미가 반감된다.
		assertThat(retryAfter).isNotNull();
		assertThat(Integer.parseInt(retryAfter)).isPositive();
	}

	@Test
	@DisplayName("한 IP가 막혀도 다른 IP는 멀쩡하다")
	void countsPerClient() throws Exception {
		for (int attempt = 0; attempt < 40; attempt++) {
			login("203.0.113.9");
		}
		assertThat(login("203.0.113.9").getResponse().getStatus()).isEqualTo(429);

		// 프록시 뒤에서 모두가 한 버킷을 쓰면 한 사람이 전체를 막을 수 있다.
		assertThat(login("198.51.100.7").getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	@DisplayName("글쓰기는 IP가 아니라 사람으로 센다")
	void countsWritesPerUser() throws Exception {
		String body = """
				{"bookId": %d, "content": "같은 IP에서 빠르게 쓴다"}""".formatted(bookId);

		int status = 200;
		for (int attempt = 0; attempt < 40 && status != 429; attempt++) {
			status = mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
							.contentType(MediaType.APPLICATION_JSON).content(body))
					.andReturn().getResponse().getStatus();
		}
		assertThat(status).isEqualTo(429);

		// 같은 IP지만 다른 사람이다. 사람 단위로 세지 않으면 여기서도 막혀,
		// 한 사무실에서 한 명이 몰아 쓰면 나머지가 글을 못 쓴다.
		mockMvc.perform(post("/api/v1/posts").header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("막힐 때도 공통 에러 형식을 지킨다")
	void keepsErrorShape() throws Exception {
		for (int attempt = 0; attempt < 40; attempt++) {
			login("203.0.113.9");
		}

		mockMvc.perform(post("/api/v1/auth/login").with(request -> {
					request.setRemoteAddr("203.0.113.9");
					return request;
				})
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"me@example.com","password":"틀린비밀번호"}"""))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("C003"))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	/**
	 * 로그인 제한은 <b>실패만</b> 센다.
	 *
	 * <p>성공한 로그인을 세면 막는 것이 없다. 무차별 대입은 실패로 이뤄지고, 공격자에게
	 * 성공은 이미 끝난 뒤다. 반대로 성공까지 세면 정상 사용이 걸린다 — 시드 스크립트처럼
	 * 한 곳에서 여러 계정으로 로그인하는 도구가 곧장 막힌다.
	 */
	@Test
	@DisplayName("성공한 로그인은 아무리 많아도 막지 않는다")
	void doesNotThrottleSuccessfulLogins() throws Exception {
		userRepository.save(User.create("real@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "real", "진짜"));

		int status = 0;
		for (int attempt = 0; attempt < 40; attempt++) {
			status = mockMvc.perform(post("/api/v1/auth/login").with(request -> {
						request.setRemoteAddr("203.0.113.50");
						return request;
					})
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"real@example.com","password":"123456789"}"""))
					.andReturn().getResponse().getStatus();
			if (status != 200) {
				break;
			}
		}

		assertThat(status).isEqualTo(200);
	}
}
