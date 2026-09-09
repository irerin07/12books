package com.irene.twelvebooks.book;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static com.irene.twelvebooks.support.SignedBookRequests.signatureOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookSignature bookSignature;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;

	private String bookJson;

	@BeforeEach
	void setUp() {
		bookRepository.deleteAll();
		bearer = "Bearer " + jwtProvider.createAccessToken(1L, "irene");
		bookJson = """
				{"isbn13":"9788960777330","title":"코드 컴플리트","authors":"스티브 맥코넬",
				 "publisher":"위키북스","thumbnailUrl":"https://example.com/c.jpg",
				 "publishedAt":"2017-05-10","signature":"%s"}"""
				.formatted(signatureOf(bookSignature, "9788960777330", "코드 컴플리트", "스티브 맥코넬",
						"위키북스", "https://example.com/c.jpg", LocalDate.of(2017, 5, 10)));
	}

	@Test
	@DisplayName("책을 등록하면 201과 내부 id를 돌려준다")
	void registersBook() throws Exception {
		mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(bookJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.title").value("코드 컴플리트"));
	}

	@Test
	@DisplayName("같은 책을 다시 등록해도 새 행이 생기지 않고 같은 id가 온다")
	void registeringTwiceKeepsOneRow() throws Exception {
		String first = mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(bookJson))
				.andReturn().getResponse().getContentAsString();
		String second = mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(bookJson))
				.andReturn().getResponse().getContentAsString();

		assertThat(second).isEqualTo(first);
		assertThat(bookRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("등록한 책을 id로 조회한다")
	void readsBook() throws Exception {
		Long id = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트", "스티브 맥코넬", "위키북스", null, null)).getId();

		mockMvc.perform(get("/api/v1/books/" + id).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isbn13").value("9788960777330"));
	}

	@Test
	@DisplayName("없는 책은 404")
	void returnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/books/999999").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("B001"));
	}

	@Test
	@DisplayName("제목이 없으면 400")
	void rejectsInvalidRegistration() throws Exception {
		mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"isbn13":"9788960777330","title":"","authors":"저자","signature":"%s"}"""
								.formatted(signatureOf(bookSignature, "9788960777330", "", "저자",
										null, null, null))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]").exists());
	}

	@Test
	@DisplayName("토큰 없이는 등록도 조회도 막힌다")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/books")
						.contentType(MediaType.APPLICATION_JSON).content(bookJson))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/books/1"))
				.andExpect(status().isUnauthorized());
	}
}
