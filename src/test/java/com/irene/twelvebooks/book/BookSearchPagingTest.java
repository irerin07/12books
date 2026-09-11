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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

	/**
	 * 쪽번호에 상한을 두지 않는다. 멈추는 판단은 {@code is_end}가 한다.
	 *
	 * <p>상한을 두면 우리가 {@code hasNext: true}라고 말해 놓고 다음 쪽 요청을 400으로 거절하는
	 * 모순이 생긴다. 실측하면 카카오는 상한 너머에도 200을 주고 {@code is_end}만 참으로 바꾼다 —
	 * 그 신호를 그대로 전달하는 편이 우리가 숫자를 지어내는 것보다 정확하다.
	 */
	@Test
	@DisplayName("깊은 쪽도 막지 않고 카카오에 넘긴다")
	void doesNotCapDeepPages() throws Exception {
		kakaoResponds("""
				{"total_count":52388,"pageable_count":1000,"is_end":true}""");

		mockMvc.perform(get("/api/v1/books/search").param("q", "사랑").param("page", "500")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(500))
				.andExpect(jsonPath("$.hasNext").value(false));
	}

	/**
	 * 검색 결과도 비어 있는 값을 빼야 하는데, 이 객체는 <b>그대로 되돌려보내 등록하는</b>
	 * 페이로드이기도 하다. 필드가 빠져도 서명이 그대로 맞아야 한다 — 빠진 것과 {@code null}인
	 * 것은 서버에서 같은 값이기 때문이다.
	 */
	@Test
	@DisplayName("ISBN 없는 책은 응답에서 isbn13이 빠지고, 그대로 등록된다")
	void omitsMissingIsbnAndStillRegisters() throws Exception {
		StubbedKakao.server.expect(requestTo(containsString("/v3/search/book")))
				.andRespond(withSuccess("""
						{"documents":[{"title":"어느 무명 시집","authors":["무명"],
						"publisher":"","isbn":"","thumbnail":"","datetime":""}],
						"meta":{"total_count":1,"pageable_count":1,"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		String body = mockMvc.perform(get("/api/v1/books/search").param("q", "무명")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				// doesNotExist()는 값이 null이어도 통과한다. 키 자체가 없는지 보려면 이쪽이다.
				.andExpect(jsonPath("$.items[0].isbn13").doesNotHaveJsonPath())
				.andExpect(jsonPath("$.items[0].publishedAt").doesNotHaveJsonPath())
				.andReturn().getResponse().getContentAsString();

		// 받은 그대로 되돌려보낸다. 빠진 필드는 보내지 않는다.
		Map<String, Object> item = com.jayway.jsonpath.JsonPath.parse(body).read("$.items[0]");
		mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(item)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("어느 무명 시집"));
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
