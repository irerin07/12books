package com.irene.twelvebooks.e2e;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.book.BookSignature;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static com.irene.twelvebooks.support.SignedBookRequests.signatureOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2의 핵심 여정. 검색 → 등록 → 재등록 → 조회.
 * 카카오는 스텁한다 — 테스트가 외부 네트워크나 API 키에 의존하면 CI에서 깨진다.
 */
@Import(BookJourneyE2ETest.StubbedKakao.class)
class BookJourneyE2ETest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	BookSignature bookSignature;

	private String bearer;

	@BeforeEach
	void setUp() {
		bookRepository.deleteAll();
		bearer = "Bearer " + jwtProvider.createAccessToken(1L, "irene");
		StubbedKakao.server.reset();
	}

	@Test
	@DisplayName("검색해서 고른 책을 등록하고 다시 조회한다")
	void searchThenRegisterThenRead() throws Exception {
		StubbedKakao.server.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/search/book")))
				.andRespond(withSuccess("""
						{"documents":[{"title":"코드 컴플리트","authors":["스티브 맥코넬"],
						"publisher":"위키북스","isbn":"8960777331 9788960777330",
						"thumbnail":"https://example.com/c.jpg",
						"datetime":"2017-05-10T00:00:00.000+09:00"}],"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		// 1. 검색 — 결과는 저장되지 않는다
		String searched = mockMvc.perform(get("/api/v1/books/search").param("q", "코드")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].isbn13").value("9788960777330"))
				.andExpect(jsonPath("$[0].authors").value("스티브 맥코넬"))
				// 서명이 붙어 나온다. 이 값이 있어야 등록할 수 있다.
				.andExpect(jsonPath("$[0].signature").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		assertThat(bookRepository.count()).isZero();

		// 2. 고른 책을 그대로 되돌려보내 등록 — 이때 처음 내부에 확정된다
		String signature = com.jayway.jsonpath.JsonPath.parse(searched).read("$[0].signature");
		String chosen = """
				{"isbn13":"9788960777330","title":"코드 컴플리트","authors":"스티브 맥코넬",
				 "publisher":"위키북스","thumbnailUrl":"https://example.com/c.jpg",
				 "publishedAt":"2017-05-10","signature":"%s"}""".formatted(signature);

		String registered = mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(chosen))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		Long bookId = com.jayway.jsonpath.JsonPath.parse(registered).read("$.id", Long.class);
		assertThat(bookRepository.count()).isEqualTo(1);

		// 3. 다른 사람이 같은 책을 담아도 행이 늘지 않는다
		mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(chosen))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(bookId));

		assertThat(bookRepository.count()).isEqualTo(1);

		// 4. 내부 id로 조회
		mockMvc.perform(get("/api/v1/books/" + bookId).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("코드 컴플리트"));
	}

	@Test
	@DisplayName("카카오가 죽어도 502로 끝나고 나머지 API는 살아 있다")
	void kakaoOutageDoesNotTakeDownTheRest() throws Exception {
		StubbedKakao.server.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/search/book")))
				.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

		mockMvc.perform(get("/api/v1/books/search").param("q", "코드").header("Authorization", bearer))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("E001"));

		// 검색이 죽은 뒤에도 등록·조회는 그대로 동작한다 — 서명 검증은 카카오를 부르지 않는다
		mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"isbn13":"9788960777330","title":"코드 컴플리트","authors":"스티브 맥코넬",
								 "publisher":"위키북스","signature":"%s"}"""
								.formatted(signatureOf(bookSignature, "9788960777330", "코드 컴플리트",
										"스티브 맥코넬", "위키북스", null, null))))
				.andExpect(status().isCreated());
	}

	@TestConfiguration
	static class StubbedKakao {

		static MockRestServiceServer server;

		@Bean
		@Primary
		RestClient stubbedRestClient() {
			RestClient.Builder builder = RestClient.builder();
			server = MockRestServiceServer.bindTo(builder).build();
			return builder.build();
		}
	}
}
