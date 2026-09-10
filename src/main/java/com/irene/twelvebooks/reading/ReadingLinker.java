package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 감상평이 매달릴 서재 기록을 찾아 주고, 없으면 만든다.
 *
 * <p>글을 쓰려면 그 책이 서재에 있어야 한다고 요구하면 문턱이 하나 늘어난다. 대신 서버가
 * "읽는 중"으로 만들어 붙인다 — 글을 썼다는 것은 그 책을 읽고 있다는 뜻이므로 상태를 지어내는
 * 것이 아니다.
 *
 * <p><b>이미 있는 기록의 상태는 건드리지 않는다.</b> 잠시 덮어 둔({@code PAUSED}) 책에 감상을
 * 남긴다고 해서 사용자가 직접 정한 상태를 서버가 되돌리면 안 된다.
 *
 * <p>트랜잭션을 걸지 않는 이유는 재조회를 살리기 위해서다 — {@link ReadingInserter} 참고.
 */
@Service
public class ReadingLinker {

	private final ReadingRepository readingRepository;
	private final ReadingInserter readingInserter;
	private final Clock clock;

	ReadingLinker(ReadingRepository readingRepository, ReadingInserter readingInserter, Clock clock) {
		this.readingRepository = readingRepository;
		this.readingInserter = readingInserter;
		this.clock = clock;
	}

	public Reading linkOrCreate(Long userId, Long bookId) {
		return readingRepository.findByUserIdAndBookId(userId, bookId)
				.orElseGet(() -> insertOrReRead(userId, bookId));
	}

	/**
	 * 사전 조회와 insert 사이는 비어 있다 — 같은 사람이 같은 책에 글 두 개를 동시에 올리면
	 * 두 요청이 함께 "없음"을 본다. 유니크 제약 위반을 "누가 먼저 만들었다"는 신호로 읽어
	 * 그 행을 다시 조회한다.
	 */
	private Reading insertOrReRead(Long userId, Long bookId) {
		try {
			return readingInserter.insert(
					Reading.of(userId, bookId, ReadingStatus.READING, LocalDateTime.now(clock)));
		}
		catch (DataIntegrityViolationException e) {
			return readingRepository.findByUserIdAndBookId(userId, bookId)
					.orElseThrow(() -> new BusinessException(ErrorCode.READING_NOT_FOUND));
		}
	}
}
