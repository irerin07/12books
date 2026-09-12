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
 * 같은 책에 첫 감상평을 동시에 쓰는 순서. 두 요청이 함께 "서재에 없음"을 보고, 한쪽이 먼저
 * 서재 기록을 만들어 커밋한다.
 *
 * <p>늦은 쪽은 중복 키로 막힌 뒤 이미 만들어진 기록을 찾아야 하는데, <b>바깥 트랜잭션의
 * 스냅샷</b>(MySQL 기본 REPEATABLE READ)에는 그 행이 아직 없다. 평범한 재조회로는 영원히
 * 찾지 못한다 — 안쪽 트랜잭션을 격리해도 바깥의 읽기 시점까지 옮겨 주지는 않기 때문이다.
 *
 * <p>스레드로 재현하면 불안정하므로 첫 조회 직후에 승자가 커밋된 상태를 만든다.
 * 늦게 도착한 요청이 겪는 바로 그 순서다.
 */
class PostWriteAgainstConcurrentShelvingTest extends AbstractIntegrationTest {

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
	@DisplayName("첫 조회 뒤 남이 먼저 서재를 만들어도, 늦은 글이 그 기록에 붙는다")
	void linksToWinnerShelvedAfterFirstLookup() throws Exception {
		// 리포지토리는 인터페이스 프록시라 실제 메서드를 부를 수 없다. 조회는 현재 트랜잭션에
		// 묶인 EntityManager로 직접 날린다 — 바깥 트랜잭션의 스냅샷을 그대로 쓴다는 점이 핵심이다.
		AtomicInteger calls = new AtomicInteger();
		willAnswer(invocation -> {
			Optional<Reading> found = lookUp();
			// 첫 조회 직후, 다른 요청이 같은 (user, book)을 서재에 담고 커밋한다.
			if (calls.getAndIncrement() == 0) {
				shelveInSeparateTransaction();
			}
			return found;
		}).given(readingRepository).findShelvedByUserIdAndBookId(myId, bookId);

		mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"47~92쪽까지 읽었다"}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.readingId").isNumber());

		// 서재 기록은 하나뿐이고, 글이 그 행에 붙어 있다.
		Reading winner = readingRepository.findAll().get(0);
		assertThat(readingRepository.count()).isEqualTo(1);
		assertThat(postRepository.findAll().get(0).getReadingId()).isEqualTo(winner.getId());
	}

	private Optional<Reading> lookUp() {
		return entityManager.createQuery(
						"select r from Reading r where r.userId = :userId and r.bookId = :bookId",
						Reading.class)
				.setParameter("userId", myId).setParameter("bookId", bookId)
				.getResultList().stream().findFirst();
	}

	private void shelveInSeparateTransaction() {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		template.executeWithoutResult(status -> readingRepository.saveAndFlush(
				Reading.of(myId, bookId, ReadingStatus.READING, LocalDateTime.now())));
	}
}
