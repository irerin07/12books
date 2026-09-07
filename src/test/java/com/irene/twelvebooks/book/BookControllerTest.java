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

class BookControllerTest extends AbstractIntegrationTest {

	private static final String BOOK_JSON = """
			{"isbn13":"9788960777330","title":"코드 컴플리트","authors":"스티브 맥코넬",
			 "publisher":"위키북스","thumbnailUrl":"https://example.com/c.jpg","publishedAt":"2017-05-10"}""";

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
	@DisplayName("책을 등록하면 201과 내부 id를 돌려준다")
	void registersBook() throws Exception {
		mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.title").value("코드 컴플리트"))
				.andExpect(jsonPath("$.pageCount").doesNotExist());
	}

	@Test
	@DisplayName("같은 책을 다시 등록해도 새 행이 생기지 않고 같은 id가 온다")
	void registeringTwiceKeepsOneRow() throws Exception {
		String first = mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
				.andReturn().getResponse().getContentAsString();
		String second = mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
				.andReturn().getResponse().getContentAsString();

		assertThat(second).isEqualTo(first);
		assertThat(bookRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("등록한 책을 id로 조회한다")
	void readsBook() throws Exception {
		Long id = bookRepository.save(Book.builder().isbn13("9788960777330")
				.title("코드 컴플리트").authors("스티브 맥코넬").publisher("위키북스").build()).getId();

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
								{"isbn13":"9788960777330","title":"","authors":"저자"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
	}

	@Test
	@DisplayName("토큰 없이는 등록도 조회도 막힌다")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/books")
						.contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/books/1"))
				.andExpect(status().isUnauthorized());
	}
}
