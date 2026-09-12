package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.reading.dto.ReadingCreateRequest;
import com.irene.twelvebooks.reading.dto.ReadingUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ReadingService {

	private static final Logger log = LoggerFactory.getLogger(ReadingService.class);

	private final ReadingRepository readingRepository;
	private final BookRepository bookRepository;
	private final Clock clock;

	public ReadingService(ReadingRepository readingRepository, BookRepository bookRepository, Clock clock) {
		this.readingRepository = readingRepository;
		this.bookRepository = bookRepository;
		this.clock = clock;
	}

	/**
	 * 책을 서재에 담는다.
	 *
	 * <p>전에 뺐던 책이면 <b>되살린다.</b> 뺀 기록의 행이 남아 있어 {@code uk(user_id, book_id)}에
	 * 걸리기 때문이기도 하지만, 그 제약의 본뜻("한 사람이 한 책을 서재에 한 번")은 뺀 뒤에도
	 * 옳다. 되살리면서 진도·별점은 초기화한다 — {@link Reading#revive} 참고.
	 *
	 * <p>확인과 되살리기 사이를 잠금으로 직렬화한다. 그러지 않으면 동시에 들어온 두 요청이
	 * 함께 "지워져 있다"를 보고 둘 다 201로 답한다. <b>행이 있는 것을 안 뒤에만</b> 잠그는데,
	 * 없는 행을 잠금 읽기하면 갭 잠금이 걸려 이어지는 insert가 막힐 수 있어서다.
	 *
	 * <p>처음 담는 책이면 그냥 넣는다. 사전 조회와 insert 사이도 비어 있어 동시 요청이 함께
	 * 통과할 수 있으므로, 마지막 방어선은 여전히 유니크 제약이다. 제약 위반을 409로 바꿔
	 * 던지기만 하므로 트랜잭션이 rollback-only가 되는 것은 문제가 되지 않는다.
	 */
	@Transactional
	public Reading add(Long userId, ReadingCreateRequest request) {
		if (!bookRepository.existsById(request.bookId())) {
			// FK 위반으로 흘려보내면 "이미 담긴 책"과 구분되지 않는 409가 된다.
			throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
		}

		Optional<Reading> existing = readingRepository.findByUserIdAndBookId(userId, request.bookId());
		if (existing.isPresent()) {
			Reading reading = readingRepository.findAnyByIdForUpdate(existing.get().getId())
					.orElseThrow(() -> new BusinessException(ErrorCode.READING_NOT_FOUND));
			if (!reading.isDeleted()) {
				throw new BusinessException(ErrorCode.READING_ALREADY_EXISTS);
			}
			reading.revive(request.statusOrDefault(), now());
			return reading;
		}

		try {
			return readingRepository.saveAndFlush(
					Reading.of(userId, request.bookId(), request.statusOrDefault(), now()));
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.READING_ALREADY_EXISTS);
		}
	}

	/**
	 * 진도·상태·별점의 부분 수정. 보내지 않은 필드는 건드리지 않는다.
	 *
	 * <p>상태·총 쪽수·진도는 {@link Reading#apply}에 함께 넘겨 최종 조합으로 판단한다.
	 *
	 * <p>읽기는 행 잠금을 건다. 이 셋은 서로 얽힌 필드라 두 요청이 각자의 스냅샷 위에서
	 * 옳게 고쳐도 합쳐진 결과가 규칙을 깰 수 있다.
	 */
	@Transactional
	public Reading update(Long userId, Long readingId, ReadingUpdateRequest request) {
		Reading reading = mine(userId, readingId);
		try {
			// 상태·총 쪽수·진도를 한 번에 넘긴다. 셋은 서로 얽혀 있어 따로 적용하면
			// 최종 상태가 멀쩡한 요청도 중간 상태에 걸리거나, 보낸 값이 조용히 덮인다.
			reading.apply(request.status(), request.pageCount(), request.currentPage(), now());
			if (request.rating() != null) {
				reading.updateRating(request.rating());
			}
		}
		catch (IllegalArgumentException e) {
			// 엔티티의 불변식은 "쪽수가 총 쪽수를 넘는다"처럼 필드 하나만 봐서는 알 수 없는
			// 규칙이라 DTO 검증이 잡지 못한다. 클라이언트 입력 문제이므로 500이 아니라 400이다.
			// 구체적인 사유는 로그에만 남긴다 — 응답에 내부 메시지를 싣지 않는다.
			log.debug("서재 기록 수정이 불변식에 걸렸습니다: readingId={}", readingId, e);
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return reading;
	}

	/**
	 * 서재에서 뺀다 — 행은 남기고 플래그만 세운다.
	 *
	 * <p>이 책에 쓴 감상평은 그대로 남는다. 예전에는 {@code on delete set null}이 글의 연결을
	 * 비웠는데, 행이 남으므로 이제 연결도 그대로 남는다. 글에 실리는 것은 연결 자체가 아니라
	 * 책 정보라 화면은 달라지지 않고, "그때 이 기록에 매달려 쓴 글"이라는 사실이 보존된다.
	 */
	@Transactional
	public void remove(Long userId, Long readingId) {
		readingRepository.softDelete(mine(userId, readingId).getId(), now());
	}

	/**
	 * 있는 기록이면서 내 것인지 함께 본다.
	 *
	 * <p>남의 기록에 404가 아니라 403을 주는 것은 의도다. 서재는 공개 정보라 존재 자체가
	 * 비밀이 아니고, 404로 감추면 "내 기록인데 왜 없다고 하지"라는 혼란만 만든다.
	 */
	private Reading mine(Long userId, Long readingId) {
		Reading reading = readingRepository.findByIdForUpdate(readingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.READING_NOT_FOUND));
		if (!reading.ownedBy(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		return reading;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
