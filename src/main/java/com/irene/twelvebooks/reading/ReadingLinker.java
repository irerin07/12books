package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

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
	 * 연결한 기록을 <b>호출자의 트랜잭션이 끝날 때까지</b> 붙잡는다.
	 *
	 * <p>연결과 저장 사이는 비어 있다. 그 틈에 다른 요청이 같은 기록을 서재에서 빼고 커밋하면,
	 * 뒤늦게 그 id로 insert하는 글이 외래 키 검사에 걸려 사용자에게 500이 된다.
	 * {@code on delete set null}은 <b>이미 저장된</b> 글만 지키므로 이 순서를 막지 못한다.
	 *
	 * <p>행 잠금을 잡으면 삭제가 호출자의 커밋을 기다린다. 글이 먼저 저장되고, 그다음 삭제가
	 * {@code set null}로 연결을 비운다 — "서재에서 빼도 글은 남는다"와 같은 결말이다.
	 *
	 * <p>이미 지워진 뒤라면 빈 값을 돌려준다. 되살리지 않는 이유는 방금 사용자가 뺀 책을
	 * 서버가 도로 담는 셈이 되기 때문이다. 연결 없는 글은 삭제 이후의 정상 상태이기도 하다.
	 *
	 * <p>{@code MANDATORY}인 것은 이 잠금이 호출자의 트랜잭션 안에서만 뜻을 갖기 때문이다.
	 * 혼자 트랜잭션을 열면 이 메서드가 끝나는 순간 잠금이 풀려 아무것도 막지 못한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Long> holdForWrite(Long readingId) {
		return readingRepository.findByIdForUpdate(readingId).map(Reading::getId);
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
