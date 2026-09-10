package com.irene.twelvebooks.reading;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * insert 시도만 따로 떼어 <b>독립 트랜잭션</b>에서 실행한다.
 *
 * <p>같은 트랜잭션 안에서 유니크 제약에 걸리면 그 트랜잭션은 재조회도 커밋도 할 수 없다 —
 * "실패하면 이미 있는 것을 다시 읽는다"는 복구 자체가 불가능해진다. REQUIRES_NEW면 실패가
 * 안쪽 트랜잭션과 함께 끝나고 호출자는 깨끗한 상태로 신호만 받는다.
 * {@code BookInserter}와 같은 이유, 같은 모양이다.
 */
@Component
class ReadingInserter {

	private final ReadingRepository readingRepository;

	ReadingInserter(ReadingRepository readingRepository) {
		this.readingRepository = readingRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Reading insert(Reading reading) {
		return readingRepository.saveAndFlush(reading);
	}
}
