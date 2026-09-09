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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadingControllerTest extends AbstractIntegrationTest {

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
	private String otherBearer;
	private Long bookId;

	@BeforeEach
	void setUp() {
		readingRepository.deleteAll();
		bookRepository.deleteAll();
		userRepository.deleteAll();

		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
	}

	private String addBook() throws Exception {
		return mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	@DisplayName("책을 담으면 읽고 싶다 상태로 서재에 들어간다")
	void addsBookAsWantToRead() throws Exception {
		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.bookId").value(bookId))
				.andExpect(jsonPath("$.status").value("WANT_TO_READ"))
				.andExpect(jsonPath("$.currentPage").value(0))
				.andExpect(jsonPath("$.startedAt").doesNotExist());
	}

	@Test
	@DisplayName("읽는 중으로 담으면 시작일이 바로 채워진다")
	void addsBookAsReading() throws Exception {
		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"status":"READING"}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("READING"))
				.andExpect(jsonPath("$.startedAt").isNotEmpty());
	}

	@Test
	@DisplayName("같은 책을 두 번 담을 수 없다")
	void rejectsDuplicateBook() throws Exception {
		addBook();

		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d}""".formatted(bookId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("R002"));

		assertThat(readingRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("없는 책은 담을 수 없다")
	void rejectsUnknownBook() throws Exception {
		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":999999}"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("B001"));
	}

	@Test
	@DisplayName("진도와 총 쪽수와 별점을 함께 고친다")
	void updatesProgress() throws Exception {
		Long id = com.jayway.jsonpath.JsonPath.parse(addBook()).read("$.id", Long.class);

		mockMvc.perform(patch("/api/v1/readings/" + id).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pageCount":320,"currentPage":100,"rating":4}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pageCount").value(320))
				.andExpect(jsonPath("$.currentPage").value(100))
				.andExpect(jsonPath("$.rating").value(4));
	}

	@Test
	@DisplayName("보내지 않은 필드는 바뀌지 않는다")
	void leavesUnsentFieldsAlone() throws Exception {
		Long id = com.jayway.jsonpath.JsonPath.parse(addBook()).read("$.id", Long.class);
		mockMvc.perform(patch("/api/v1/readings/" + id).header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"pageCount":320,"currentPage":100}"""));

		mockMvc.perform(patch("/api/v1/readings/" + id).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"rating":5}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPage").value(100))
				.andExpect(jsonPath("$.pageCount").value(320))
				.andExpect(jsonPath("$.rating").value(5));
	}

	@Test
	@DisplayName("완독으로 바꾸면 완독일이 채워지고, 되돌리면 비워진다")
	void finishAndReopen() throws Exception {
		Long id = com.jayway.jsonpath.JsonPath.parse(addBook()).read("$.id", Long.class);

		mockMvc.perform(patch("/api/v1/readings/" + id).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"FINISHED"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.finishedAt").isNotEmpty());

		mockMvc.perform(patch("/api/v1/readings/" + id).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"READING"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.finishedAt").doesNotExist())
				.andExpect(jsonPath("$.startedAt").isNotEmpty());
	}

	@Test
	@DisplayName("총 쪽수를 넘는 진도는 400이다")
	void rejectsProgressBeyondPageCount() throws Exception {
		Long id = com.jayway.jsonpath.JsonPath.parse(addBook()).read("$.id", Long.class);
		mockMvc.perform(patch("/api/v1/readings/" + id).header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"pageCount":320}"""));

		mockMvc.perform(patch("/api/v1/readings/" + id).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"currentPage":321}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
	}

	@Test
	@DisplayName("남의 기록은 고칠 수도 지울 수도 없다")
	void protectsOtherPeoplesReadings() throws Exception {
		Long id = com.jayway.jsonpath.JsonPath.parse(addBook()).read("$.id", Long.class);

		mockMvc.perform(patch("/api/v1/readings/" + id).header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"currentPage":10}"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("A004"));

		mockMvc.perform(delete("/api/v1/readings/" + id).header("Authorization", otherBearer))
				.andExpect(status().isForbidden());

		assertThat(readingRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("서재에서 뺀다")
	void removesFromLibrary() throws Exception {
		Long id = com.jayway.jsonpath.JsonPath.parse(addBook()).read("$.id", Long.class);

		mockMvc.perform(delete("/api/v1/readings/" + id).header("Authorization", bearer))
				.andExpect(status().isNoContent());

		assertThat(readingRepository.count()).isZero();
	}

	@Test
	@DisplayName("없는 기록은 404")
	void returnsNotFound() throws Exception {
		mockMvc.perform(patch("/api/v1/readings/999999").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"currentPage":10}"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("R001"));
	}

	@Test
	@DisplayName("토큰 없이는 담을 수 없다")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/readings")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d}""".formatted(bookId)))
				.andExpect(status().isUnauthorized());
	}
}
