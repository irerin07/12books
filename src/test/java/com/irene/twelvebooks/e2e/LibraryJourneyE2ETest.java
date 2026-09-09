package com.irene.twelvebooks.e2e;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.reading.ReadingGoalRepository;
import com.irene.twelvebooks.reading.ReadingRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3의 핵심 여정. 검색 → 등록 → 서재에 담기 → 진도 → 완독 → 서재 조회 → 목표.
 *
 * <p>여기서 처음으로 "혼자 쓰는 독서 기록 앱"이 끝까지 이어진다. 카카오는 스텁한다.
 */
@Import(LibraryJourneyE2ETest.StubbedKakao.class)
class LibraryJourneyE2ETest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ReadingGoalRepository readingGoalRepository;

	@Autowired
	ReadingRepository readingRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;
	private String otherBearer;

	@BeforeEach
	void setUp() {
		StubbedKakao.server.reset();

		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());
	}

	@Test
	@DisplayName("검색한 책을 서재에 담아 끝까지 읽고 서재와 목표에 반영된다")
	void searchRegisterShelveReadFinish() throws Exception {
		StubbedKakao.server.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/search/book")))
				.andRespond(withSuccess("""
						{"documents":[{"title":"코드 컴플리트","authors":["스티브 맥코넬"],
						"publisher":"위키북스","isbn":"8960777331 9788960777330",
						"thumbnail":"https://example.com/c.jpg",
						"datetime":"2017-05-10T00:00:00.000+09:00"}],"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		// 1. 검색 — 서명이 붙어 나온다
		String searched = mockMvc.perform(get("/api/v1/books/search").param("q", "코드")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String signature = com.jayway.jsonpath.JsonPath.parse(searched).read("$[0].signature");

		// 2. 고른 책을 그대로 되돌려보내 등록
		String registered = mockMvc.perform(post("/api/v1/books").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"isbn13":"9788960777330","title":"코드 컴플리트","authors":"스티브 맥코넬",
								 "publisher":"위키북스","thumbnailUrl":"https://example.com/c.jpg",
								 "publishedAt":"2017-05-10","signature":"%s"}""".formatted(signature)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long bookId = com.jayway.jsonpath.JsonPath.parse(registered).read("$.id", Long.class);

		// 3. 서재에 담는다 — 읽는 중으로
		String shelved = mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"status":"READING"}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startedAt").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		Long readingId = com.jayway.jsonpath.JsonPath.parse(shelved).read("$.id", Long.class);

		// 4. 총 쪽수를 채우고 진도를 올린다
		mockMvc.perform(patch("/api/v1/readings/" + readingId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pageCount":964,"currentPage":300}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPage").value(300));

		// 5. 남의 기록은 건드릴 수 없다
		mockMvc.perform(patch("/api/v1/readings/" + readingId).header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"currentPage":1}"""))
				.andExpect(status().isForbidden());

		// 6. 완독 — 완독일이 남고 진도가 끝까지 간다
		mockMvc.perform(patch("/api/v1/readings/" + readingId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"FINISHED","rating":5}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.finishedAt").isNotEmpty())
				.andExpect(jsonPath("$.currentPage").value(964))
				.andExpect(jsonPath("$.rating").value(5));

		// 7. 서재에 반영된다 — 표지까지 함께
		mockMvc.perform(get("/api/v1/users/irene/library").param("status", "FINISHED")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].book.title").value("코드 컴플리트"))
				.andExpect(jsonPath("$.items[0].book.thumbnailUrl").isNotEmpty());

		// 8. 올해 목표에 완독 한 권이 잡힌다
		int thisYear = java.time.Year.now().getValue();
		mockMvc.perform(put("/api/v1/me/goals/" + thisYear).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"targetCount":12}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetCount").value(12))
				.andExpect(jsonPath("$.finishedCount").value(1));

		// 9. 다시 읽기 시작하면 완독일이 비워진다 — 재독은 새 행을 만들지 않는다
		mockMvc.perform(patch("/api/v1/readings/" + readingId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"READING"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.finishedAt").doesNotExist())
				.andExpect(jsonPath("$.startedAt").isNotEmpty());

		assertThat(readingRepository.count()).isEqualTo(1);
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
