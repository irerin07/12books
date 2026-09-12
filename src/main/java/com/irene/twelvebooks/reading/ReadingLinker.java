package com.irene.twelvebooks.reading;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
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

	/**
	 * 글이 붙을 서재 기록을 정하고, 호출자의 트랜잭션이 끝날 때까지 <b>그 행을 잠근다</b>.
	 *
	 * <p>잠그는 이유는 연결과 저장 사이가 비어 있기 때문이다. 그 틈에 다른 요청이 같은 기록을
	 * 서재에서 빼고 커밋하면, 뒤늦게 그 id로 insert하는 글이 외래 키 검사에 걸려 사용자에게
	 * 500이 된다 — {@code on delete set null}은 <b>이미 저장된</b> 글만 지킨다. 잠가 두면 삭제가
	 * 호출자의 커밋을 기다리고, 글이 먼저 저장된 뒤 삭제가 연결을 비운다. "서재에서 빼도 글은
	 * 남는다"와 같은 결말이다.
	 *
	 * <p>빈 값은 "붙일 기록이 없다"는 뜻이다. 잠그기 전에 이미 지워진 경우인데, 되살리지 않는
	 * 것은 방금 사용자가 뺀 책을 서버가 도로 담는 셈이 되기 때문이다. 연결 없는 글은 삭제 이후의
	 * 정상 상태이기도 하다.
	 *
	 * <p>{@code MANDATORY}인 것은 잠금이 호출자의 트랜잭션 안에서만 뜻을 갖기 때문이다.
	 * 혼자 트랜잭션을 열면 이 메서드가 끝나는 순간 풀려 아무것도 막지 못한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Long> linkForWrite(Long userId, Long bookId) {
		// 첫 조회는 잠그지 않는다. 행이 없을 때 잠금 읽기는 갭 잠금을 잡고, 그러면 바로 아래의
		// insert(독립 트랜잭션)가 자기 요청이 잡은 갭 잠금에 막혀 스스로를 기다리게 된다.
		Optional<Reading> found = readingRepository.findShelvedByUserIdAndBookId(userId, bookId);
		if (found.isPresent()) {
			return lock(found.get().getId());
		}
		// 전에 읽다 뺀 책이면 그 기록을 이어서 쓴다. 새 행을 만들면 사용자가 고르지도 않은
		// "새로 시작"을 서버가 대신 고른 셈이 되고, 그 책에 두 벌의 기록이 생긴다.
		Optional<Reading> past = readingRepository.findPastIds(userId, bookId, PageRequest.ofSize(1))
				.stream().findFirst()
				.flatMap(readingRepository::findById);
		if (past.isPresent()) {
			past.get().shelveAgain(ReadingStatus.READING, LocalDateTime.now(clock));
			return Optional.of(past.get().getId());
		}
		try {
			return lock(readingInserter.insert(
					Reading.of(userId, bookId, ReadingStatus.READING, LocalDateTime.now(clock))).getId());
		}
		catch (DataIntegrityViolationException e) {
			// 사전 조회와 insert 사이도 비어 있다 — 같은 사람이 같은 책에 글 둘을 동시에 올리면
			// 두 요청이 함께 "없음"을 본다. 유니크 제약 위반은 "누가 먼저 만들었다"는 신호다.
			//
			// 여기서 반드시 잠금 읽기여야 한다. REPEATABLE READ에서 평범한 재조회는 이 트랜잭션이
			// 시작할 때의 스냅샷을 계속 보므로 방금 남이 커밋한 그 행을 찾지 못한다. insert를
			// 독립 트랜잭션으로 격리해도 바깥 트랜잭션의 읽기 시점까지 옮겨 주지는 않는다.
			//
			return readingRepository.findShelvedByUserIdAndBookIdForUpdate(userId, bookId)
					.map(Reading::getId);
		}
	}

	/** 승자 행을 PK로 잠근다. 이미 지워졌다면 빈 값이고, 그때는 연결 없이 쓴다. */
	private Optional<Long> lock(Long readingId) {
		return readingRepository.findByIdForUpdate(readingId).map(Reading::getId);
	}
}
