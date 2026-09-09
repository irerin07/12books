package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.reading.dto.GoalResponse;
import com.irene.twelvebooks.reading.dto.LibraryItemResponse;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LibraryService {

	private final ReadingRepository readingRepository;
	private final ReadingGoalRepository readingGoalRepository;
	private final BookRepository bookRepository;
	private final UserRepository userRepository;

	public LibraryService(ReadingRepository readingRepository, ReadingGoalRepository readingGoalRepository,
			BookRepository bookRepository, UserRepository userRepository) {
		this.readingRepository = readingRepository;
		this.readingGoalRepository = readingGoalRepository;
		this.bookRepository = bookRepository;
		this.userRepository = userRepository;
	}

	/**
	 * 남의 서재도 볼 수 있다 — 프로필의 표지 그리드가 이것으로 그려지므로 공개다.
	 *
	 * <p>책은 페이지의 reading들을 모아 <b>한 번에</b> 조회한다. 항목마다 따로 읽으면 페이지
	 * 크기만큼 쿼리가 늘어난다. 이 방식은 페이지가 20건이든 50건이든 쿼리가 두 번이다.
	 */
	@Transactional(readOnly = true)
	public CursorPage<LibraryItemResponse> library(String handle, LibraryFilter filter, Long cursor, int size) {
		User owner = userRepository.findByHandle(handle)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		List<Reading> rows = readingRepository.findLibraryPage(owner.getId(), filter.status(),
				filter.from(filter.year()), filter.to(filter.year()),
				filter.from(filter.startedYear()), filter.to(filter.startedYear()),
				filter.from(filter.finishedYear()), filter.to(filter.finishedYear()),
				cursor, PageRequest.ofSize(size + 1));

		CursorPage<Reading> page = CursorPage.of(rows, size, Reading::getId);
		Map<Long, Book> books = booksOf(page.items());

		return new CursorPage<>(
				page.items().stream()
						.map(reading -> LibraryItemResponse.of(reading, books.get(reading.getBookId())))
						.toList(),
				page.nextCursor(), page.hasNext());
	}

	private Map<Long, Book> booksOf(List<Reading> readings) {
		List<Long> bookIds = readings.stream().map(Reading::getBookId).distinct().toList();
		return bookRepository.findAllById(bookIds).stream()
				.collect(Collectors.toMap(Book::getId, Function.identity()));
	}

	/**
	 * 연간 목표를 세우거나 고친다.
	 *
	 * <p>"조회해서 없으면 만든다"가 아니라 한 문장의 upsert로 처리한다. 전자는 같은 사용자·연도에
	 * 두 요청이 동시에 들어올 때 둘 다 "없음"을 보고 하나가 유니크 제약에 걸려 500이 된다.
	 * PUT은 본래 멱등이므로 DB에게 그대로 시키는 편이 단순하고 정확하다.
	 */
	@Transactional
	public GoalResponse setGoal(Long userId, int year, int targetCount) {
		readingGoalRepository.upsert(userId, year, targetCount);
		long finished = readingRepository.countFinishedBetween(userId,
				LocalDateTime.of(year, 1, 1, 0, 0), LocalDateTime.of(year + 1, 1, 1, 0, 0));
		return new GoalResponse(year, targetCount, finished);
	}
}
