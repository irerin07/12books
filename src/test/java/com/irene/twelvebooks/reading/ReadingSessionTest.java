package com.irene.twelvebooks.reading;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 같은 책을 다시 담을 때 <b>이어서 읽을지 새로 시작할지</b>를 사용자가 고른다.
 *
 * <p>고르게 하지 않으면 둘 중 하나를 서버가 말없이 정하게 되는데, 어느 쪽이든 사용자를
 * 놀라게 한다. 예전 진도를 되살리면 지운 줄 알았던 것이 돌아오고, 0쪽부터 시작하면 읽은
 * 기록이 사라진 것처럼 보인다.
 *
 * <p>그래서 "새로 시작"은 <b>새 행</b>이다. 같은 행을 0쪽으로 되돌리면 그 선택이 곧 지난
 * 기록을 덮어쓰는 일이 되는데, 그건 이 프로젝트가 지우지 않기로 한 바로 그 데이터다.
 */
class ReadingSessionTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ReadingRepository readingRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;
	private Long bookId;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
	}

	/** 200쪽까지 읽다가 서재에서 뺀 상태를 만든다. */
	private long readAndUnshelve() throws Exception {
		String body = mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookId\": %d, \"status\": \"READING\"}".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long readingId = ((Number) JsonPath.parse(body).read("$.id")).longValue();

		mockMvc.perform(patch("/api/v1/readings/" + readingId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pageCount\": 500, \"currentPage\": 200}"))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/readings/" + readingId).header("Authorization", bearer))
				.andExpect(status().isNoContent());
		return readingId;
	}

	@Test
	@DisplayName("전에 담았던 책을 그냥 다시 담으면 고르라고 되돌려보낸다")
	void requiresChoiceWhenPreviousReadingExists() throws Exception {
		readAndUnshelve();

		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookId\": %d, \"status\": \"READING\"}".formatted(bookId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("R003"));
	}

	@Test
	@DisplayName("이어서 읽기를 고르면 그 기록이 진도 그대로 서재에 돌아온다")
	void resumesPreviousReading() throws Exception {
		long readingId = readAndUnshelve();

		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "status": "READING", "resume": true}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(readingId))
				.andExpect(jsonPath("$.currentPage").value(200))
				.andExpect(jsonPath("$.pageCount").value(500));

		// 행이 늘지 않는다 — 있던 기록을 다시 꽂은 것이다.
		assertThat(readingRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("새로 시작을 고르면 새 기록이 0쪽부터 생기고 지난 기록은 그대로 남는다")
	void startsNewReadingSession() throws Exception {
		long previousId = readAndUnshelve();

		String body = mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "status": "READING", "resume": false}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.currentPage").value(0))
				.andExpect(jsonPath("$.pageCount").doesNotHaveJsonPath())
				.andReturn().getResponse().getContentAsString();
		long freshId = ((Number) JsonPath.parse(body).read("$.id")).longValue();

		assertThat(freshId).isNotEqualTo(previousId);
		// 지난 독서는 지워지지 않는다. 두 번 읽었다는 사실이 데이터로 남는다.
		assertThat(readingRepository.count()).isEqualTo(2);
		assertThat(readingRepository.findById(previousId).orElseThrow().getCurrentPage()).isEqualTo(200);

		// 그래도 서재에 보이는 것은 하나다.
		mockMvc.perform(get("/api/v1/users/irene/library").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].reading.id").value(freshId));
	}

	@Test
	@DisplayName("처음 담는 책에는 고르라고 하지 않는다")
	void doesNotAskForFirstShelving() throws Exception {
		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookId\": %d, \"status\": \"READING\"}".formatted(bookId)))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("서재에 이미 있으면 고르기 전에 409다")
	void rejectsWhenAlreadyShelved() throws Exception {
		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookId\": %d}".formatted(bookId)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "resume": true}""".formatted(bookId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("R002"));
	}

	@Test
	@DisplayName("이 책에 대한 내 기록을 물어보면 서재 여부와 진도를 알려준다")
	void tellsMyReadingForBook() throws Exception {
		// 담은 적이 없으면 404다. 빈 응답으로 답하면 "안 담음"과 "책이 없음"이 구분되지 않는다.
		mockMvc.perform(get("/api/v1/books/" + bookId + "/reading").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("R001"));

		long readingId = readAndUnshelve();

		// 뺀 뒤에도 답한다. 화면은 이걸로 "200쪽까지 읽으셨어요"를 띄우고 고르게 한다.
		mockMvc.perform(get("/api/v1/books/" + bookId + "/reading").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(readingId))
				.andExpect(jsonPath("$.inBookshelf").value(false))
				.andExpect(jsonPath("$.currentPage").value(200));

		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "resume": true}""".formatted(bookId)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/books/" + bookId + "/reading").header("Authorization", bearer))
				.andExpect(jsonPath("$.inBookshelf").value(true));
	}
}
