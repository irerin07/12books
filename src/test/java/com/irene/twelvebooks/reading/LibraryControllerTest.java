package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서재 조회와 연간 목표.
 *
 * <p>연도 필터가 이 테스트의 핵심이다. 하나로 뭉치면 조합이 조용히 무의미해지므로
 * 셋으로 나눴고, 그 각각이 실제로 다르게 동작하는지를 여기서 지킨다.
 */
class LibraryControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ReadingRepository readingRepository;

	@Autowired
	ReadingGoalRepository readingGoalRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());

		// 2025에 시작해 2026에 끝낸 책
		save(me.getId(), "9788960777001", ReadingStatus.FINISHED,
				LocalDateTime.of(2025, 12, 1, 10, 0), LocalDateTime.of(2026, 1, 20, 10, 0));
		// 2026에 시작해 아직 읽는 중인 책
		save(me.getId(), "9788960777002", ReadingStatus.READING,
				LocalDateTime.of(2026, 3, 1, 10, 0), null);
		// 아직 펴지도 않은 책
		save(me.getId(), "9788960777003", ReadingStatus.WANT_TO_READ, null, null);
	}

	private void save(Long userId, String isbn13, ReadingStatus status,
			LocalDateTime startedAt, LocalDateTime finishedAt) {
		Long bookId = bookRepository.save(Book.withIsbn13(isbn13, "책 " + isbn13, "저자",
				"출판사", "https://example.com/c.jpg", null)).getId();
		Reading reading = Reading.of(userId, bookId, ReadingStatus.WANT_TO_READ, LocalDateTime.now());
		if (startedAt != null) {
			reading.changeStatus(ReadingStatus.READING, startedAt);
		}
		if (finishedAt != null) {
			reading.changeStatus(ReadingStatus.FINISHED, finishedAt);
		}
		if (status != ReadingStatus.FINISHED && finishedAt == null && startedAt != null) {
			reading.changeStatus(status, startedAt);
		}
		readingRepository.save(reading);
	}

	@Test
	@DisplayName("서재는 최신순으로 오고 책 정보가 함께 붙는다")
	void listsLibraryWithBooks() throws Exception {
		mockMvc.perform(get("/api/v1/users/irene/library").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(3))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.items[0].book.title").isNotEmpty())
				.andExpect(jsonPath("$.items[0].reading.status").isNotEmpty());
	}

	@Test
	@DisplayName("status로 거른다")
	void filtersByStatus() throws Exception {
		mockMvc.perform(get("/api/v1/users/irene/library").param("status", "READING")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].reading.status").value("READING"));
	}

	@Test
	@DisplayName("finishedYear는 그 해에 다 읽은 책만 준다")
	void filtersByFinishedYear() throws Exception {
		mockMvc.perform(get("/api/v1/users/irene/library").param("finishedYear", "2026")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].reading.status").value("FINISHED"));
	}

	@Test
	@DisplayName("startedYear는 그 해에 읽기 시작한 책만 준다")
	void filtersByStartedYear() throws Exception {
		mockMvc.perform(get("/api/v1/users/irene/library").param("startedYear", "2025")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].reading.status").value("FINISHED"));
	}

	@Test
	@DisplayName("year는 그 해에 손댄 책을 전부 준다 — 시작이든 완독이든")
	void yearCoversBothEnds() throws Exception {
		// 2026: 1월에 끝낸 책 + 3월에 시작한 책
		mockMvc.perform(get("/api/v1/users/irene/library").param("year", "2026")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2));
	}

	@Test
	@DisplayName("year와 status를 함께 줘도 빈 목록이 되지 않는다")
	void yearWithReadingStatusIsMeaningful() throws Exception {
		mockMvc.perform(get("/api/v1/users/irene/library")
						.param("year", "2026").param("status", "READING")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].reading.status").value("READING"));
	}

	@Test
	@DisplayName("startedYear와 finishedYear는 AND로 묶인다")
	void combinesStartedAndFinishedYears() throws Exception {
		mockMvc.perform(get("/api/v1/users/irene/library")
						.param("startedYear", "2025").param("finishedYear", "2026")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1));

		mockMvc.perform(get("/api/v1/users/irene/library")
						.param("startedYear", "2026").param("finishedYear", "2026")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	@DisplayName("커서로 다음 장을 가져온다")
	void pagesWithCursor() throws Exception {
		String first = mockMvc.perform(get("/api/v1/users/irene/library").param("size", "2")
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();

		Long cursor = com.jayway.jsonpath.JsonPath.parse(first).read("$.nextCursor", Long.class);

		mockMvc.perform(get("/api/v1/users/irene/library")
						.param("size", "2").param("cursor", String.valueOf(cursor))
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("없는 사용자의 서재는 404")
	void returnsNotFoundForUnknownUser() throws Exception {
		mockMvc.perform(get("/api/v1/users/nobody/library").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("U003"));
	}

	@Test
	@DisplayName("목표를 세우면 그 값과 그 해 완독 수를 함께 돌려준다")
	void setsGoalAndReturnsFinishedCount() throws Exception {
		mockMvc.perform(put("/api/v1/me/goals/2026").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"targetCount":20}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.year").value(2026))
				.andExpect(jsonPath("$.targetCount").value(20))
				// 2026에 다 읽은 책은 한 권이다
				.andExpect(jsonPath("$.finishedCount").value(1));
	}

	@Test
	@DisplayName("같은 해에 다시 세우면 덮어쓴다 — 행이 늘지 않는다")
	void replacesGoalForTheSameYear() throws Exception {
		mockMvc.perform(put("/api/v1/me/goals/2026").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"targetCount":20}"""));

		mockMvc.perform(put("/api/v1/me/goals/2026").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"targetCount":30}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetCount").value(30));

		org.assertj.core.api.Assertions.assertThat(readingGoalRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("목표 권수가 범위를 벗어나면 400")
	void rejectsOutOfRangeGoal() throws Exception {
		mockMvc.perform(put("/api/v1/me/goals/2026").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"targetCount":0}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
	}
}
