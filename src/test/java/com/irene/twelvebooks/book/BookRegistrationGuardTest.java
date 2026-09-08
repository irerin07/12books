package com.irene.twelvebooks.book;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 등록 경로가 받아들여선 안 되는 입력들. books는 공용 테이블이라 한 번 오염되면
 * 이후 같은 ISBN을 담는 모든 사용자가 오염된 행을 받는다.
 */
class BookRegistrationGuardTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;

	@BeforeEach
	void setUp() {
		bookRepository.deleteAll();
		bearer = "Bearer " + jwtProvider.createAccessToken(1L, "irene");
	}

	@Test
	@DisplayName("검색을 거치지 않고 지어낸 메타데이터를 등록할 수 없다")
	void rejectsUnsignedRegistration() throws Exception {
		mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"isbn13":"9788960777330","title":"조작된 제목","authors":"조작된 저자",
								 "publisher":"위키북스"}"""))
				.andExpect(status().isBadRequest());

		assertThat(bookRepository.count()).isZero();
	}

	@Test
	@DisplayName("ISBN13은 숫자 13자리여야 한다 — X 13개는 ISBN이 아니다")
	void rejectsNonNumericIsbn13() throws Exception {
		mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"isbn13":"XXXXXXXXXXXXX","title":"제목","authors":"저자"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'isbn13')]").exists());
	}

	@Test
	@DisplayName("검색 page가 1 미만이면 카카오까지 가지 않고 400이다")
	void rejectsNonPositivePage() throws Exception {
		mockMvc.perform(get("/api/v1/books/search").param("q", "코드").param("page", "0")
						.header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
	}

	@Test
	@DisplayName("page가 숫자가 아니면 400이다 — 변환 실패는 클라이언트 잘못이다")
	void rejectsNonNumericPage() throws Exception {
		mockMvc.perform(get("/api/v1/books/search").param("q", "코드").param("page", "abc")
						.header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
	}

	@Test
	@DisplayName("검색어 파라미터가 아예 없으면 400이다")
	void rejectsMissingQuery() throws Exception {
		mockMvc.perform(get("/api/v1/books/search").header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
	}

	@Test
	@DisplayName("책 id가 숫자가 아니면 400이다")
	void rejectsNonNumericPathVariable() throws Exception {
		mockMvc.perform(get("/api/v1/books/not-a-number").header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
	}

	@Test
	@DisplayName("검색어가 비면 카카오까지 가지 않고 400이다")
	void rejectsBlankQuery() throws Exception {
		mockMvc.perform(get("/api/v1/books/search").param("q", " ")
						.header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
	}
}
