package com.irene.twelvebooks.user;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.auth.RefreshCookies;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴.
 *
 * <p><b>행을 지우지 않는다.</b> 이 프로젝트의 규약을 탈퇴에서도 그대로 지킨다 — 계정에 시각을
 * 세우고, 그 사람과 관련된 것이 남에게 보이지 않게 한다. 보관 기간이 지난 뒤의 실제 파기는
 * 별도 작업이다.
 *
 * <p>그래서 두 가지를 함께 봐야 한다. <b>사라졌는가</b>(남의 화면에서)와 <b>남아 있는가</b>(행이).
 * 앞만 보면 하드 삭제도 통과하고, 뒤만 보면 탈퇴하지 않은 것과 구별되지 않는다.
 *
 * <p>행이 남으면 유니크 제약이 이메일과 handle을 붙잡는다. 같은 주소로 다시 가입할 수 없으면
 * 탈퇴가 <b>영구 추방</b>이 되므로, 살아 있는 계정에만 걸리는 부분 유니크가 필요하다.
 */
class WithdrawalTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private String bearer;
	private String otherBearer;
	private Long bookId;
	private Long postId;

	@BeforeEach
	void setUp() throws Exception {
		User me = userRepository.save(User.create("me@example.com",
				passwordEncoder.encode("123456789"), "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com",
				passwordEncoder.encode("123456789"), "other", "남"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		String body = mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "47~92쪽. 이름 짓기에 지면을 이렇게까지 쓴다."}
								""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		postId = ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private MvcResult withdraw(String password) throws Exception {
		return mockMvc.perform(delete("/api/v1/me").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"password": "%s"}""".formatted(password)))
				.andReturn();
	}

	@Test
	@DisplayName("탈퇴하면 그 사람의 글과 프로필이 남에게 보이지 않는다")
	void hidesEverythingFromOthers() throws Exception {
		assertThat(withdraw("123456789").getResponse().getStatus()).isEqualTo(204);

		mockMvc.perform(get("/api/v1/posts/{id}", postId).header("Authorization", otherBearer))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/feed").header("Authorization", otherBearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/books/{id}/posts", bookId).header("Authorization", otherBearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		// 프로필은 없는 사람과 같이 답한다. 탈퇴했다고 알려 주면 그 자체가 정보다.
		mockMvc.perform(get("/api/v1/users/{handle}", "irene").header("Authorization", otherBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("U003"));
	}

	@Test
	@DisplayName("탈퇴해도 행은 남는다")
	void keepsTheRows() throws Exception {
		withdraw("123456789");

		// 행을 지우면 "무엇이 있었나"에 답할 수 없다. 보관 기간이 지난 뒤의 파기는 별도 작업이다.
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from users where email = ?", Integer.class, "me@example.com"))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from posts where id = ?", Integer.class, postId))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select deleted_at is not null from users where email = ?", Boolean.class,
				"me@example.com")).isTrue();
	}

	@Test
	@DisplayName("탈퇴한 계정으로는 로그인할 수 없고 쓰던 세션도 끊긴다")
	void closesTheDoorBehind() throws Exception {
		Cookie refresh = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "me@example.com", "password": "123456789"}"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie(RefreshCookies.NAME);
		assertThat(refresh).isNotNull();

		withdraw("123456789");

		// 세션을 그대로 두면 탈퇴 뒤에도 계속 쓸 수 있다.
		mockMvc.perform(post("/api/v1/auth/reissue").cookie(refresh))
				.andExpect(status().isUnauthorized());
		// 로그인이 되면 탈퇴가 되돌려진 것과 같다.
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "me@example.com", "password": "123456789"}"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A002"));
	}

	@Test
	@DisplayName("같은 이메일과 handle로 다시 가입할 수 있다")
	void allowsSigningUpAgain() throws Exception {
		withdraw("123456789");

		// 행이 남아 있어도 살아 있는 계정에만 유니크가 걸려야 한다. 아니면 탈퇴가 영구 추방이다.
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "me@example.com", "password": "123456789",
								 "handle": "irene", "displayName": "아이린"}"""))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("비밀번호가 틀리면 탈퇴하지 않는다")
	void requiresThePassword() throws Exception {
		assertThat(withdraw("틀린비밀번호").getResponse().getStatus()).isEqualTo(401);

		mockMvc.perform(get("/api/v1/users/{handle}", "irene").header("Authorization", otherBearer))
				.andExpect(status().isOk());
	}

	/**
	 * 탈퇴자의 댓글은 목록에서 빠진다. <b>숫자도 함께 빠져야 한다.</b>
	 *
	 * <p>조회 조건만 더하면 "댓글 1개"를 눌렀는데 아무것도 없는 화면이 된다. 운영자 숨김에서
	 * 이미 한 번 겪은 것과 같은 자리다.
	 */
	@Test
	@DisplayName("탈퇴하면 그 사람이 단 댓글만큼 댓글 수도 줄어든다")
	void adjustsCommentCount() throws Exception {
		Long othersPost = writePost(otherBearer, "남이 쓴 글이다.");
		comment(bearer, othersPost, "탈퇴할 사람의 댓글");
		comment(otherBearer, othersPost, "남는 댓글");

		withdraw("123456789");

		mockMvc.perform(get("/api/v1/posts/{id}/comments", othersPost)
						.header("Authorization", otherBearer))
				.andExpect(jsonPath("$.items.length()").value(1));
		mockMvc.perform(get("/api/v1/posts/{id}", othersPost).header("Authorization", otherBearer))
				.andExpect(jsonPath("$.commentCount").value(1));
	}

	private Long writePost(String who, String content) throws Exception {
		String body = mockMvc.perform(post("/api/v1/posts").header("Authorization", who)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "%s"}""".formatted(bookId, content)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private void comment(String who, Long postId, String content) throws Exception {
		mockMvc.perform(post("/api/v1/posts/{id}/comments", postId).header("Authorization", who)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"content": "%s"}""".formatted(content)))
				.andExpect(status().isCreated());
	}

	/**
	 * 탈퇴자는 관계 목록에서도 빠진다.
	 *
	 * <p>프로필 단건만 막으면 남의 팔로워 목록에 이름·handle·사진이 그대로 남는다. 같은
	 * handle로 새 계정이 가입하면 <b>목록에는 옛 사람이 보이는데 링크는 새 사람으로 간다</b> —
	 * 엉뚱한 사람을 팔로우하게 된다.
	 *
	 * <p>거르는 자리는 <b>쿼리</b>다. 받아 온 뒤에 빼면 스무 개를 청구했는데 열여덟 개가 오는
	 * 페이지가 된다.
	 */
	@Test
	@DisplayName("탈퇴자는 팔로워·팔로잉 목록과 그 수에서 빠진다")
	void disappearsFromRelations() throws Exception {
		mockMvc.perform(post("/api/v1/users/{handle}/follow", "other").header("Authorization", bearer))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/users/{handle}/follow", "irene").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		withdraw("123456789");

		mockMvc.perform(get("/api/v1/users/{handle}/followers", "other")
						.header("Authorization", otherBearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/users/{handle}/followings", "other")
						.header("Authorization", otherBearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		// 목록에서 뺐는데 숫자가 그대로면 "팔로워 1명"을 눌렀을 때 빈 화면이 된다.
		mockMvc.perform(get("/api/v1/users/{handle}", "other").header("Authorization", otherBearer))
				.andExpect(jsonPath("$.followerCount").value(0))
				.andExpect(jsonPath("$.followingCount").value(0));
	}

	/**
	 * 탈퇴 뒤에 남아 있는 access 토큰으로 쓰는 경우.
	 *
	 * <p>access의 남은 수명을 허용하는 것과 <b>보이지 않는 글의 숫자만 늘어나는 것</b>은 다른
	 * 문제다. 쓰기는 성공하고 카운터는 오르는데 목록에서는 빠지므로, "댓글 1개"를 눌렀을 때
	 * 아무것도 없는 화면이 된다.
	 */
	@Test
	@DisplayName("탈퇴한 뒤에는 남은 토큰으로도 쓸 수 없다")
	void refusesWritesAfterWithdrawal() throws Exception {
		Long othersPost = writePost(otherBearer, "남이 쓴 글이다.");
		withdraw("123456789");

		mockMvc.perform(post("/api/v1/posts/{id}/comments", othersPost).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"content": "탈퇴하고도 쓴다"}"""))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "탈퇴하고도 쓴다"}""".formatted(bookId)))
				.andExpect(status().isUnauthorized());

		// 거절됐으니 숫자도 그대로여야 한다.
		mockMvc.perform(get("/api/v1/posts/{id}", othersPost).header("Authorization", otherBearer))
				.andExpect(jsonPath("$.commentCount").value(0));
	}

	/**
	 * 프로필 수정도 쓰기다.
	 *
	 * <p>글·댓글은 막으면서 프로필만 열어 두면, 탈퇴한 행의 이름·소개·사진을 남은 access
	 * 토큰으로 계속 바꿀 수 있다. 지금은 그 프로필이 남에게 보이지 않지만, <b>보이지 않는 것과
	 * 바꿀 수 있는 것은 다른 문제다</b> — 보관 기간이 끝나 파기하거나 되살리는 작업이 생기면
	 * 그때 꺼내는 값이 탈퇴 시점의 값이 아니게 된다.
	 *
	 * <p>거절은 글·댓글과 같은 401이다. 404로 답하면 "계정이 없다"가 되어 탈퇴한 본인이
	 * 자기 계정의 상태를 오해한다.
	 */
	@Test
	@DisplayName("탈퇴한 뒤에는 남은 토큰으로 프로필을 고칠 수 없다")
	void refusesProfileUpdateAfterWithdrawal() throws Exception {
		withdraw("123456789");

		mockMvc.perform(patch("/api/v1/me").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"displayName": "탈퇴하고도 고친다"}"""))
				.andExpect(status().isUnauthorized());

		// 거절됐으니 남은 행의 값도 그대로여야 한다.
		assertThat(jdbcTemplate.queryForObject(
				"select display_name from users where handle = 'irene'", String.class))
				.isEqualTo("아이린");
	}
}
