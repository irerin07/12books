package com.irene.twelvebooks.post;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.reading.Reading;
import com.irene.twelvebooks.reading.ReadingRepository;
import com.irene.twelvebooks.reading.ReadingStatus;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import jakarta.persistence.EntityManager;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <p>실제로는 행 잠금이 삭제를 커밋까지 기다리게 하므로 이 순서는 잠금을 잡기 <b>전</b>에만
 * 성립한다. 스레드로 재현하면 불안정하므로 조회 직후 삭제가 커밋된 상태를 만든다.
 */
class PostWriteAgainstReadingRemovalTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	PostRepository postRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	EntityManager entityManager;

	@MockitoSpyBean
	ReadingRepository readingRepository;

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
		Long readingId = readingRepository.saveAndFlush(
				Reading.of(myId, bookId, ReadingStatus.READING, LocalDateTime.now())).getId();

		// 조회는 되지만, 잠그기 전에 다른 요청이 그 기록을 서재에서 빼고 커밋한 상태를 만든다.
		// 리포지토리는 인터페이스 프록시라 실제 메서드를 부를 수 없어 EntityManager로 직접 읽는다.
		AtomicInteger calls = new AtomicInteger();
		willAnswer(invocation -> {
			Optional<Reading> found = lookUp();
			if (calls.getAndIncrement() == 0) {
				removeInSeparateTransaction(readingId);
			}
			return found;
		}).given(readingRepository).findByUserIdAndBookId(myId, bookId);

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

	private Optional<Reading> lookUp() {
		return entityManager.createQuery(
						"select r from Reading r where r.userId = :userId and r.bookId = :bookId",
						Reading.class)
				.setParameter("userId", myId).setParameter("bookId", bookId)
				.getResultList().stream().findFirst();
	}

	private void removeInSeparateTransaction(Long readingId) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		template.executeWithoutResult(status -> readingRepository.deleteById(readingId));
	}
}
