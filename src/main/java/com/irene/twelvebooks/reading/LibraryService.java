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
				filter.year(), filter.startedYear(), filter.finishedYear(), cursor,
				PageRequest.ofSize(size + 1));

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
	 * 연간 목표를 세우거나 고친다. 같은 해에 다시 세우면 행을 늘리지 않고 덮어쓴다 —
	 * uk(user_id, year)가 그것을 강제하기도 하지만, 목표는 "그 해에 하나"라는 개념 자체가 그렇다.
	 */
	@Transactional
	public GoalResponse setGoal(Long userId, int year, int targetCount) {
		ReadingGoal goal = readingGoalRepository.findByUserIdAndYear(userId, year)
				.orElseGet(() -> readingGoalRepository.save(ReadingGoal.of(userId, year, targetCount)));
		goal.updateTargetCount(targetCount);
		return new GoalResponse(year, goal.getTargetCount(), readingRepository.countFinishedIn(userId, year));
	}
}
