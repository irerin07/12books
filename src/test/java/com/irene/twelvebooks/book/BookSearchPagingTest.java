package com.irene.twelvebooks.book;

import com.irene.twelvebooks.auth.JwtProvider;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색 결과의 페이지 정보.
 *
 * <p>맨 배열로 내려주면 클라이언트가 <b>다음 페이지가 있는지 알 수 없다.</b> 결과가 비면
 * "끝"인지 "원래 없음"인지도 구분되지 않는다. 카카오는 {@code meta}로 그것을 알려주는데
 * 우리가 읽지 않고 버리고 있었다.
 */
@Import(BookSearchPagingTest.StubbedKakao.class)
class BookSearchPagingTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;

	@BeforeEach
	void setUp() {
		bearer = "Bearer " + jwtProvider.createAccessToken(1L, "irene");
		StubbedKakao.server.reset();
	}

	private void kakaoResponds(String meta) {
		StubbedKakao.server.expect(requestTo(containsString("/v3/search/book")))
				.andRespond(withSuccess("""
						{"documents":[{"title":"코드 컴플리트","authors":["스티브 맥코넬"],
						"publisher":"위키북스","isbn":"8960777331 9788960777330",
						"thumbnail":"https://example.com/c.jpg",
						"datetime":"2017-05-10T00:00:00.000+09:00"}],"meta":%s}
						""".formatted(meta), MediaType.APPLICATION_JSON));
	}

	@Test
	@DisplayName("검색 결과는 목록과 함께 페이지 정보를 준다")
	void carriesPageInformation() throws Exception {
		kakaoResponds("""
				{"total_count":137,"pageable_count":137,"is_end":false}""");

		mockMvc.perform(get("/api/v1/books/search").param("q", "코드").param("page", "2")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].isbn13").value("9788960777330"))
				.andExpect(jsonPath("$.items[0].signature").isNotEmpty())
				.andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.hasNext").value(true))
				.andExpect(jsonPath("$.totalCount").value(137));
	}

	@Test
	@DisplayName("카카오가 마지막 페이지라고 하면 hasNext는 거짓이다")
	void marksTheLastPage() throws Exception {
		kakaoResponds("""
				{"total_count":3,"pageable_count":3,"is_end":true}""");

		mockMvc.perform(get("/api/v1/books/search").param("q", "코드").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.totalCount").value(3));
	}

	@Test
	@DisplayName("결과가 없어도 빈 목록과 함께 페이지 정보가 온다")
	void reportsEmptyResults() throws Exception {
		StubbedKakao.server.expect(requestTo(containsString("/v3/search/book")))
				.andRespond(withSuccess("""
						{"documents":[],"meta":{"total_count":0,"pageable_count":0,"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		mockMvc.perform(get("/api/v1/books/search").param("q", "없는책")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.totalCount").value(0));
	}

	/**
	 * {@code meta}가 통째로 빠진 응답도 실패시키지 않는다. 카카오가 문서는 정상으로 주는데
	 * 메타만 빠뜨렸다면 검색 자체는 성립하고, 그때는 "더 없다"로 보수적으로 답한다.
	 */
	@Test
	@DisplayName("meta가 없으면 더 없는 것으로 본다")
	void treatsMissingMetaAsTheLastPage() throws Exception {
		StubbedKakao.server.expect(requestTo(containsString("/v3/search/book")))
				.andRespond(withSuccess("""
						{"documents":[{"title":"코드 컴플리트","authors":["스티브 맥코넬"],
						"publisher":"위키북스","isbn":"8960777331 9788960777330",
						"thumbnail":"","datetime":""}]}
						""", MediaType.APPLICATION_JSON));

		mockMvc.perform(get("/api/v1/books/search").param("q", "코드").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.totalCount").value(0));
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
