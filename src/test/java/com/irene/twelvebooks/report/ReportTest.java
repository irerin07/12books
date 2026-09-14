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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 신고를 받는다.
 *
 * <p>신고 버튼만 있고 쌓이는 곳이 없으면 신고 기능을 만든 것이 아니다. 여기서 지키는 것은
 * <b>접수</b>까지다 — 운영자가 보고 처리하는 쪽은 {@code AdminReportTest}가 지킨다.
 */
class ReportTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;
	private String authorBearer;
	private Long postId;

	@BeforeEach
	void setUp() throws Exception {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User author = userRepository.save(User.create("author@example.com", "hash", "author", "글쓴이"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), author.getHandle());

		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		String body = mockMvc.perform(post("/api/v1/posts").header("Authorization", authorBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "47~92쪽. 이름 짓기에 지면을 이렇게까지 쓴다."}
								""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		postId = ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private org.springframework.test.web.servlet.ResultActions report(String path, Object id,
			String bearer) throws Exception {
		return mockMvc.perform(post("/api/v1" + path, id).header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reason": "ABUSE", "detail": "욕설이 섞여 있습니다."}"""));
	}

	@Test
	@DisplayName("남의 글을 신고하면 접수된다")
	void reportsPost() throws Exception {
		mockMvc.perform(post("/api/v1/posts/{id}/reports", postId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "ABUSE", "detail": "욕설이 섞여 있습니다."}"""))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("같은 대상을 다시 신고하면 409다")
	void refusesDuplicateReport() throws Exception {
		report("/posts/{id}/reports", postId, bearer).andExpect(status().isNoContent());

		// 한 사람이 여러 번 넣을 수 있으면 신고 수를 부풀려 우선순위를 조작할 수 있다.
		report("/posts/{id}/reports", postId, bearer)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("S002"));
	}

	@Test
	@DisplayName("자기 글은 신고할 수 없다")
	void refusesSelfReport() throws Exception {
		report("/posts/{id}/reports", postId, authorBearer)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("S003"));
	}

	@Test
	@DisplayName("없는 대상은 신고할 수 없다")
	void refusesMissingTarget() throws Exception {
		// 없는 대상의 신고를 받아 두면 운영자 목록에 열어 볼 수 없는 줄이 생긴다.
		report("/posts/{id}/reports", 9999L, bearer).andExpect(status().isNotFound());
		report("/comments/{id}/reports", 9999L, bearer).andExpect(status().isNotFound());
		report("/users/{handle}/reports", "nobody", bearer).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("댓글과 사람도 신고할 수 있다")
	void reportsCommentAndUser() throws Exception {
		String body = mockMvc.perform(post("/api/v1/posts/{id}/comments", postId)
						.header("Authorization", authorBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"content": "저도 그 장에서 멈췄어요."}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long commentId = ((Number) JsonPath.read(body, "$.id")).longValue();

		report("/comments/{id}/reports", commentId, bearer).andExpect(status().isNoContent());
		report("/users/{handle}/reports", "author", bearer).andExpect(status().isNoContent());
	}
}
