package com.irene.twelvebooks.common.ratelimit;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

	@Autowired
	StringRedisTemplate redis;

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

	/**
	 * 한꺼번에 들이닥치는 경우.
	 *
	 * <p>"보고 나서 센다"는 방식에는 본 시점과 세는 시점 사이에 틈이 있다. 그 틈에 수십 개가
	 * 함께 검사를 통과하면 <b>한도를 넘는 만큼 비밀번호를 더 시험해 볼 수 있다.</b> 고정 창의
	 * 경계에서 두 배가 지나가는 것과는 다른 문제다 — 이쪽은 동시 요청 수만큼 커진다.
	 *
	 * <p>돌아온 응답을 전부 모아서 본다. "429가 아닌 것"만 세면 요청이 예외로 터져도 통과 수가
	 * 늘지 않아, <b>전부 실패한 상황과 잘 막은 상황을 구별하지 못한다.</b> 그래서 예외가 한 건도
	 * 없었는지, 401과 429 말고 다른 것이 섞이지 않았는지, 실제로 비밀번호를 시험해 본 횟수가
	 * 정확히 한도만큼이었는지까지 확인한다.
	 */
	@RepeatedTest(3)
	@DisplayName("한꺼번에 들이닥쳐도 한도를 넘겨 통과하지 않는다")
	void doesNotOvershootUnderConcurrency() throws Exception {
		int attempts = 60;
		CountDownLatch start = new CountDownLatch(1);
		List<Integer> statuses = new CopyOnWriteArrayList<>();
		List<Throwable> failures = new CopyOnWriteArrayList<>();
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		try {
			for (int i = 0; i < attempts; i++) {
				pool.submit(() -> {
					try {
						start.await();
						statuses.add(login("203.0.113.77").getResponse().getStatus());
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

		// 터진 요청을 조용히 넘기면 "아무것도 통과하지 못했다"가 "잘 막았다"로 읽힌다.
		assertThat(failures).isEmpty();
		assertThat(statuses).hasSize(attempts);
		// 401(자격증명 불일치)과 429(제한) 말고 다른 것이 나오면 그 자체가 문제다.
		assertThat(statuses).containsOnly(401, 429);

		// 전부 실패하는 시도다. 한도(20)만큼만 비밀번호를 시험해 볼 수 있어야 한다 —
		// 적게 통과한 것도 정상이 아니다. 막아야 할 것만 막았는지 함께 본다.
		assertThat(statuses.stream().filter(status -> status == 401).count()).isEqualTo(20);
		assertThat(statuses.stream().filter(status -> status == 429).count()).isEqualTo(attempts - 20);
	}

	/**
	 * 전달 헤더는 기본으로 믿지 않는다.
	 *
	 * <p>믿으면 IP 제한이 무의미해진다 — 헤더 한 줄만 바꾸면 매번 새 버킷을 쓴다. 프록시가
	 * 클라이언트가 보낸 헤더를 지우고 다시 쓰는 환경에서만 켤 수 있는 설정이라, 기본을
	 * 켜 두면 그 보장이 없는 곳에서 조용히 뚫린다.
	 */
	@Test
	@DisplayName("X-Forwarded-For를 지어내도 버킷이 갈리지 않는다")
	void doesNotTrustForwardedHeaderByDefault() throws Exception {
		for (int attempt = 0; attempt < 40; attempt++) {
			mockMvc.perform(post("/api/v1/auth/login")
					.header("X-Forwarded-For", "10.0.0." + attempt)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"me@example.com","password":"틀린비밀번호"}"""));
		}

		mockMvc.perform(post("/api/v1/auth/login")
						.header("X-Forwarded-For", "10.0.0.250")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"me@example.com","password":"틀린비밀번호"}"""))
				.andExpect(status().isTooManyRequests());
	}

	/**
	 * 거절된 요청은 <b>자리를 차지하지 않는다.</b>
	 *
	 * <p>막힌 요청까지 세면 두드릴수록 카운터가 부풀고, 그 부푼 값이 예약을 돌려받은 뒤에도
	 * 남는다. 그러면 인증 실패가 한 건도 없는데 창이 끝날 때까지 정상 로그인이 막힌다 —
	 * 스무 명이 동시에 로그인하고 있을 때 다른 스무 개가 두드리기만 해도 그렇게 된다.
	 */
	@Test
	@DisplayName("막힌 요청은 한도를 더 깎지 않는다")
	void rejectedRequestsDoNotConsumeQuota() throws Exception {
		for (int attempt = 0; attempt < 35; attempt++) {
			login("203.0.113.31");
		}

		String counter = redis.keys("ratelimit:login:ip:203.0.113.31*").stream()
				.findFirst()
				.map(key -> redis.opsForValue().get(key))
				.orElse(null);

		// 한도가 20인데 35번 두드렸다. 카운터가 20에서 멈춰야 예약을 돌려받은 사람들이
		// 제 몫을 되찾는다.
		assertThat(counter).isEqualTo("20");
	}
}
