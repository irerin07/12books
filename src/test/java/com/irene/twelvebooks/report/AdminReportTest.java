package com.irene.twelvebooks.report;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쌓인 신고를 운영자가 보고 처리한다.
 *
 * <p>숨김은 <b>작성자 삭제와 다른 사건이다.</b> {@code deleted_at}을 재활용하면 "작성자가 지운
 * 글"과 "운영자가 내린 글"을 구분할 수 없고, 신고가 기각됐을 때 되돌릴 수도 없다. 그래서
 * 이 테스트는 숨긴 뒤 <b>되돌아오는 것</b>까지 함께 본다 — 앞만 보면 삭제와 구별되지 않는다.
 */
class AdminReportTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private String adminBearer;
	private String bearer;
	private String authorBearer;
	private Long bookId;
	private Long postId;

	@BeforeEach
	void setUp() throws Exception {
		User admin = userRepository.save(User.create("admin@example.com", "hash", "admin", "운영자"));
		jdbcTemplate.update("update users set role = 'ADMIN' where id = ?", admin.getId());
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User author = userRepository.save(User.create("author@example.com", "hash", "author", "글쓴이"));

		adminBearer = "Bearer " + jwtProvider.createAccessToken(admin.getId(), admin.getHandle());
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), author.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		String body = mockMvc.perform(post("/api/v1/posts").header("Authorization", authorBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "47~92쪽. 결말을 다 적어 버린다."}
								""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		postId = ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private Long reportPost() throws Exception {
		mockMvc.perform(post("/api/v1/posts/{id}/reports", postId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "SPOILER", "detail": "결말이 그대로 적혀 있습니다."}"""))
				.andExpect(status().isNoContent());
		String body = mockMvc.perform(get("/api/v1/admin/reports").param("status", "PENDING")
						.header("Authorization", adminBearer))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.items[0].id")).longValue();
	}

	private ResultActions handle(Long reportId, String decision) throws Exception {
		return mockMvc.perform(patch("/api/v1/admin/reports/{id}", reportId)
				.header("Authorization", adminBearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "%s"}""".formatted(decision)));
	}

	@Test
	@DisplayName("운영자가 아니면 신고 목록에 닿지 못한다")
	void keepsAdminPathsClosed() throws Exception {
		mockMvc.perform(get("/api/v1/admin/reports").header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("A004"));
	}

	@Test
	@DisplayName("운영자는 신고와 함께 신고당한 내용을 본다")
	void showsReportedContent() throws Exception {
		reportPost();

		mockMvc.perform(get("/api/v1/admin/reports").param("status", "PENDING")
						.header("Authorization", adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].reason").value("SPOILER"))
				.andExpect(jsonPath("$.items[0].status").value("PENDING"))
				.andExpect(jsonPath("$.items[0].targetType").value("POST"))
				.andExpect(jsonPath("$.items[0].reporterHandle").value("irene"))
				// 내용을 못 보면 운영자가 판단할 수 없다 — id만 놓고 무엇을 내릴지 정하게 된다.
				.andExpect(jsonPath("$.items[0].targetContent").value(
						"47~92쪽. 결말을 다 적어 버린다."));
	}

	@Test
	@DisplayName("숨기면 모든 조회에서 사라지고 기각하면 돌아온다")
	void hidesEverywhereAndRestores() throws Exception {
		Long reportId = reportPost();
		handle(reportId, "ACTIONED").andExpect(status().isNoContent());

		assertPostVisible(false);

		// 기각은 "보고 문제없다"는 판단이다. 되돌릴 수 없으면 운영자가 DB를 직접 만지게 된다.
		handle(reportId, "REJECTED").andExpect(status().isNoContent());
		assertPostVisible(true);
	}

	private void assertPostVisible(boolean visible) throws Exception {
		mockMvc.perform(get("/api/v1/posts/{id}", postId).header("Authorization", bearer))
				.andExpect(visible ? status().isOk() : status().isNotFound());
		int expected = visible ? 1 : 0;
		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(expected));
		mockMvc.perform(get("/api/v1/books/{id}/posts", bookId).header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(expected));
		mockMvc.perform(get("/api/v1/users/{handle}/posts", "author").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(expected));
	}

	@Test
	@DisplayName("댓글을 숨기면 목록에서 빠지고 댓글 수도 함께 줄어든다")
	void hidesComment() throws Exception {
		String body = mockMvc.perform(post("/api/v1/posts/{id}/comments", postId)
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"content": "결말 적지 마세요."}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long commentId = ((Number) JsonPath.read(body, "$.id")).longValue();

		mockMvc.perform(post("/api/v1/comments/{id}/reports", commentId)
						.header("Authorization", authorBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "ABUSE"}"""))
				.andExpect(status().isNoContent());
		String list = mockMvc.perform(get("/api/v1/admin/reports").param("status", "PENDING")
						.header("Authorization", adminBearer))
				.andReturn().getResponse().getContentAsString();
		Long reportId = ((Number) JsonPath.read(list, "$.items[0].id")).longValue();

		handle(reportId, "ACTIONED").andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		// 숫자만 남으면 "댓글 1개"를 눌렀는데 아무것도 없는 화면이 된다.
		mockMvc.perform(get("/api/v1/posts/{id}", postId).header("Authorization", bearer))
				.andExpect(jsonPath("$.commentCount").value(0));
	}
}
