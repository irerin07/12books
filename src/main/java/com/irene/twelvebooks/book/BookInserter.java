package com.irene.twelvebooks.book;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * insert 시도만 따로 떼어 <b>독립 트랜잭션</b>에서 실행한다.
 *
 * <p>같은 트랜잭션 안에서 flush가 유니크 제약에 걸리면 그 트랜잭션은 더 쓸 수 없다.
 * 영속성 컨텍스트에는 식별자가 없는 엔티티가 남아 다음 JPQL의 auto-flush가
 * {@code AssertionFailure}로 죽고, 살아남더라도 트랜잭션이 rollback-only로 찍혀
 * 커밋이 {@code UnexpectedRollbackException}이 된다. 즉 "실패하면 재조회한다"는
 * 복구 자체가 불가능해진다.
 *
 * <p>REQUIRES_NEW면 실패가 이 안쪽 트랜잭션과 함께 끝난다. 호출자는 깨끗한 상태로
 * 제약 위반만 신호로 받아 재조회할 수 있다.
 */
@Component
class BookInserter {

	private final BookRepository bookRepository;

	BookInserter(BookRepository bookRepository) {
		this.bookRepository = bookRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Book insert(Book book) {
		return bookRepository.saveAndFlush(book);
	}
}
