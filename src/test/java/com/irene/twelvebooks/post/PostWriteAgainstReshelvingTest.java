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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 감상평을 쓰는 사이에 <b>그 책을 누군가 다시 담는</b> 순서.
 *
 * <p>서재에서 뺐던 책에 글을 쓰면 지난 기록을 다시 꽂아 연결한다. 그 기록을 잠그지 않고 읽으면,
 * 읽은 시점과 쓰는 시점 사이에 다른 요청이 같은 기록을 다시 담고 진도를 옮겨도 이쪽은 모른다.
 * Hibernate는 들고 있던 옛 필드를 통째로 다시 써서 <b>앞선 변경을 덮는다</b> — 완독해 둔 책이
 * 읽는 중으로 되돌아가고 진도가 뒤로 간다.
 *
 * <p>기존 기록을 연결하는 경로에는 잠금이 있는데 다시 꽂는 경로에만 없었다. 사람이 두 경로를
 * 나란히 읽지 않으면 눈에 띄지 않는 종류의 구멍이다.
 */
class PostWriteAgainstReshelvingTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	PlatformTransactionManager transactionManager;

	@MockitoSpyBean
	ReadingRepository readingRepository;

	private Long myId;
	private Long bookId;
	private Long readingId;
	private String bearer;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		myId = me.getId();
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();

		Reading reading = Reading.of(myId, bookId, ReadingStatus.READING, LocalDateTime.now());
		reading.applyProgress(500, 200);
		reading.removeFromBookshelf();
		readingId = readingRepository.saveAndFlush(reading).getId();
	}

	@Test
	@DisplayName("글을 쓰는 사이에 그 책을 다시 담아 완독해도 그 결과가 덮이지 않는다")
	void doesNotOverwriteConcurrentReshelving() throws Exception {
		// 지난 기록의 id를 찾은 직후, 다른 요청이 그 기록을 다시 담고 완독까지 마친 상태를 만든다.
		AtomicInteger calls = new AtomicInteger();
		willAnswer(invocation -> {
			List<Long> ids = List.of(readingId);
			if (calls.getAndIncrement() == 0) {
				finishInSeparateTransaction();
			}
			return ids;
		}).given(readingRepository).findPastIds(eqLong(myId), eqLong(bookId), any());

		mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"47~92쪽까지 읽었다"}""".formatted(bookId)))
				.andExpect(status().isCreated());

		Reading result = readingRepository.findById(readingId).orElseThrow();
		assertThat(result.isInBookshelf()).isTrue();
		// 앞선 요청이 남긴 결과가 살아 있어야 한다. 덮였다면 READING·200쪽으로 되돌아간다.
		assertThat(result.getStatus()).isEqualTo(ReadingStatus.FINISHED);
		assertThat(result.getCurrentPage()).isEqualTo(500);
	}

	/** 다른 요청이 그 책을 다시 담고 끝까지 읽은 상태로 커밋한다. */
	private void finishInSeparateTransaction() {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		template.executeWithoutResult(status -> {
			Reading reading = readingRepository.findById(readingId).orElseThrow();
			reading.shelveAgain(ReadingStatus.FINISHED, LocalDateTime.now());
			readingRepository.saveAndFlush(reading);
		});
	}

	private static Long eqLong(Long value) {
		return org.mockito.ArgumentMatchers.eq(value);
	}

	private static Pageable any() {
		return org.mockito.ArgumentMatchers.any();
	}
}
