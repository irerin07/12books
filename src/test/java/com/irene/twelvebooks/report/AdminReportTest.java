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

import static org.assertj.core.api.Assertions.assertThat;
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

	@Autowired
	ReportRepository reportRepository;

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

	/** 기각하면서 대상을 다시 공개할지 함께 밝힌다. */
	private ResultActions reject(Long reportId, boolean restore) throws Exception {
		return mockMvc.perform(patch("/api/v1/admin/reports/{id}", reportId)
				.header("Authorization", adminBearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "REJECTED", "restore": %s}""".formatted(restore)));
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
		// 다만 되돌릴지는 요청이 밝힌다 — 판단과 공개는 다른 결정이다.
		reject(reportId, true).andExpect(status().isNoContent());
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

	/**
	 * 숨긴 글 아래의 댓글을 처리하는 경우.
	 *
	 * <p>운영자의 카운터 조정은 <b>부모 글이 보이든 안 보이든</b> 반영돼야 한다. 사용자 경로의
	 * 조건(살아 있고 안 숨겨진 글에만)을 그대로 쓰면, 글을 숨긴 상태에서 그 아래 댓글을 내렸을
	 * 때 숫자가 줄지 않는다. 나중에 글을 되돌리면 보이는 댓글은 하나인데 숫자는 둘이다.
	 */
	@Test
	@DisplayName("숨긴 글 아래 댓글을 내려도 댓글 수가 어긋나지 않는다")
	void keepsCommentCountWhenParentIsHidden() throws Exception {
		Long first = writeComment("먼저 단 댓글");
		writeComment("나중에 단 댓글");

		Long commentReport = reportComment(first);
		Long postReport = reportPost();

		handle(postReport, "ACTIONED").andExpect(status().isNoContent());
		handle(commentReport, "ACTIONED").andExpect(status().isNoContent());
		// 글을 다시 연다. 댓글 신고는 대상이 달라서 이 판정에 끼지 않는다.
		reject(postReport, true).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1));
		mockMvc.perform(get("/api/v1/posts/{id}", postId).header("Authorization", bearer))
				.andExpect(jsonPath("$.commentCount").value(1));
	}

	private Long writeComment(String content) throws Exception {
		String body = mockMvc.perform(post("/api/v1/posts/{id}/comments", postId)
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"content": "%s"}""".formatted(content)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	/** 글쓴이가 신고한다 - 댓글 작성자는 자기 댓글을 신고할 수 없다. */
	private Long reportComment(Long commentId) throws Exception {
		mockMvc.perform(post("/api/v1/comments/{id}/reports", commentId)
						.header("Authorization", authorBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "ABUSE"}"""))
				.andExpect(status().isNoContent());
		return latestReportId();
	}

	private Long latestReportId() throws Exception {
		String list = mockMvc.perform(get("/api/v1/admin/reports").param("status", "PENDING")
						.header("Authorization", adminBearer))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(list, "$.items[0].id")).longValue();
	}

	/**
	 * 같은 글에 신고가 여럿일 때.
	 *
	 * <p>기각은 <b>그 신고에 대한 판단</b>이지 대상 전체를 열라는 뜻이 아니다. 욕설로 내린 글을
	 * "스포일러는 아니다"라는 판단 하나로 다시 공개하면, 인정된 신고가 그대로 남아 있는데도
	 * 글이 돌아온다.
	 *
	 * <p>그래서 <b>인정된 신고가 하나라도 남아 있으면 숨김을 유지한다.</b> 마지막 하나까지
	 * 기각됐을 때 비로소 열린다.
	 */
	@Test
	@DisplayName("신고 하나를 기각해도 다른 신고가 인정돼 있으면 계속 숨긴다")
	void keepsHiddenWhileAnotherReportStands() throws Exception {
		Long abuse = reportPost();
		Long spoiler = reportPostAs(adminBearer);

		handle(abuse, "ACTIONED").andExpect(status().isNoContent());
		assertPostVisible(false);

		// "스포일러는 아니다"는 판단이지, 욕설 신고를 뒤집는 것이 아니다.
		reject(spoiler, false).andExpect(status().isNoContent());
		assertPostVisible(false);

		// 마지막 하나까지 기각되면 열 수 있다.
		reject(abuse, true).andExpect(status().isNoContent());
		assertPostVisible(true);
	}

	/**
	 * 기각과 공개는 <b>다른 판단</b>이다.
	 *
	 * <p>"이 신고의 주장은 타당하지 않다"와 "이 글을 다시 공개한다"는 같은 말이 아니다.
	 * 신고가 여럿 달린 글에서 하나를 기각하는 것은 흔한 일이고, 그때마다 글이 열리면
	 * 운영자가 의도하지 않은 공개가 된다. 그래서 서버가 짐작하지 않고 요청이 밝히게 한다.
	 *
	 * <p>고르기 전에는 <b>아무것도 바뀌지 않는다.</b> 판단만 저장되고 공개는 따로 묻는
	 * 식이면 "기각했는데 글이 그대로네"를 운영자가 뒤늦게 발견한다.
	 */
	@Test
	@DisplayName("기각하면서 공개 여부를 밝히지 않으면 고르라고 되돌려보낸다")
	void requiresRestoreChoiceOnRejection() throws Exception {
		Long reportId = reportPost();
		handle(reportId, "ACTIONED").andExpect(status().isNoContent());

		handle(reportId, "REJECTED")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("S004"));

		// 판단도 저장되지 않는다.
		assertPostVisible(false);
		assertThat(reportRepository.findById(reportId).orElseThrow().getStatus())
				.isEqualTo(ReportStatus.ACTIONED);
	}

	@Test
	@DisplayName("공개하지 않기를 고르면 판단만 남고 글은 내려 둔 채다")
	void recordsRejectionWithoutRestoring() throws Exception {
		Long reportId = reportPost();
		handle(reportId, "ACTIONED").andExpect(status().isNoContent());

		reject(reportId, false).andExpect(status().isNoContent());

		assertThat(reportRepository.findById(reportId).orElseThrow().getStatus())
				.isEqualTo(ReportStatus.REJECTED);
		assertPostVisible(false);
	}

	/**
	 * 다른 인정된 신고가 남아 있으면 <b>거절한다.</b> 조용히 넘기면 운영자는 눌렀는데
	 * 아무 일도 일어나지 않은 화면을 본다. 모르는 사실을 알려 주고 그것부터 처리하게 한다.
	 */
	@Test
	@DisplayName("다른 인정된 신고가 남아 있으면 공개 요청을 거절하고 알려준다")
	void refusesRestoreWhileAnotherReportStands() throws Exception {
		Long abuse = reportPost();
		Long spoiler = reportPostAs(adminBearer);
		handle(abuse, "ACTIONED").andExpect(status().isNoContent());
		handle(spoiler, "ACTIONED").andExpect(status().isNoContent());

		reject(spoiler, true)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("S005"));

		// 아무것도 바뀌지 않는다 — 판단도, 공개 여부도.
		assertPostVisible(false);
		assertThat(reportRepository.findById(spoiler).orElseThrow().getStatus())
				.isEqualTo(ReportStatus.ACTIONED);
	}

	/** 사람 신고는 내린 콘텐츠가 없다. 고를 것이 없으므로 묻지 않는다. */
	@Test
	@DisplayName("사람 신고를 기각할 때는 공개 여부를 묻지 않는다")
	void asksNothingForUserReports() throws Exception {
		mockMvc.perform(post("/api/v1/users/{handle}/reports", "author")
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "ABUSE"}"""))
				.andExpect(status().isNoContent());

		handle(latestReportId(), "REJECTED").andExpect(status().isNoContent());
	}

	private Long reportPostAs(String reporterBearer) throws Exception {
		mockMvc.perform(post("/api/v1/posts/{id}/reports", postId)
						.header("Authorization", reporterBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "SPOILER"}"""))
				.andExpect(status().isNoContent());
		return latestReportId();
	}
}
