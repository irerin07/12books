package com.irene.twelvebooks.post;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.reading.Reading;
import com.irene.twelvebooks.reading.ReadingLinker;
import com.irene.twelvebooks.reading.ReadingRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 감상평을 저장하는 사이에 그 서재 기록이 사라지는 순서.
 *
 * <p>{@code on delete set null}은 <b>이미 저장된</b> 글만 지킨다. 아직 insert하지 않은 글이
 * 사라진 기록의 id를 들고 있으면 외래 키 검사에 그대로 걸린다 — 사용자에게는 500이다.
 *
 * <p>스레드로 재현하면 불안정하므로 연결 직후 삭제가 <b>커밋된</b> 상태를 만든다.
 * 늦게 도착한 저장이 겪는 바로 그 상황이다.
 */
class PostWriteAgainstReadingRemovalTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	PostRepository postRepository;

	@Autowired
	ReadingRepository readingRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	PlatformTransactionManager transactionManager;

	@MockitoSpyBean
	ReadingLinker readingLinker;

	private String bearer;
	private Long myId;
	private Long bookId;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		myId = me.getId();
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
	}

	@Test
	@DisplayName("연결한 서재 기록이 저장 직전에 사라져도 글은 써지고, 연결만 비어 있다")
	void writesPostWhenReadingVanishesMidway() throws Exception {
		// 연결이 끝난 직후 다른 요청이 그 기록을 서재에서 빼고 커밋한 상태를 만든다.
		willAnswer(invocation -> {
			Reading linked = (Reading) invocation.callRealMethod();
			removeInSeparateTransaction(linked.getId());
			return linked;
		}).given(readingLinker).linkOrCreate(myId, bookId);

		mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"47~92쪽까지 읽었다"}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("47~92쪽까지 읽었다"))
				.andExpect(jsonPath("$.readingId").doesNotExist());

		assertThat(postRepository.count()).isEqualTo(1);
		assertThat(postRepository.findAll().get(0).getReadingId()).isNull();
	}

	private void removeInSeparateTransaction(Long readingId) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		template.executeWithoutResult(status -> readingRepository.deleteById(readingId));
	}
}
