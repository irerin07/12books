package com.irene.twelvebooks.reading;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

	Optional<Reading> findByUserIdAndBookId(Long userId, Long bookId);

	/**
	 * 서재 한 페이지. 주어진 필터는 전부 AND로 묶이고, 주지 않은 것은 조건에서 빠진다.
	 *
	 * <p>연도는 {@code year(started_at) = 2026}이 아니라 <b>[그 해 시작, 다음 해 시작)</b> 범위로
	 * 묻는다. 컬럼에 함수를 씌우면 인덱스를 탈 수 없어서다.
	 *
	 * <p>{@code year}는 시작과 완독 중 <b>하나라도</b> 그 해면 통과하는 편의 필터다. 이것을 완독
	 * 연도로만 두면 {@code year=2026&status=READING}이 항상 빈 목록이 된다 — 읽는 중인 책은
	 * 완독일이 없어 연도 조건에 걸릴 수가 없기 때문이다.
	 *
	 * <p>정렬은 {@code id desc}라 커서가 id 하나다. {@code size + 1}건을 가져와 다음 페이지
	 * 존재를 판정한다.
	 */
	@Query("""
			select r from Reading r
			where r.userId = :userId
			  and (:status is null or r.status = :status)
			  and (:startedFrom is null or (r.startedAt >= :startedFrom and r.startedAt < :startedTo))
			  and (:finishedFrom is null or (r.finishedAt >= :finishedFrom and r.finishedAt < :finishedTo))
			  and (:yearFrom is null
			       or (r.startedAt >= :yearFrom and r.startedAt < :yearTo)
			       or (r.finishedAt >= :yearFrom and r.finishedAt < :yearTo))
			  and (:cursor is null or r.id < :cursor)
			order by r.id desc
			""")
	List<Reading> findLibraryPage(@Param("userId") Long userId,
			@Param("status") ReadingStatus status,
			@Param("yearFrom") LocalDateTime yearFrom, @Param("yearTo") LocalDateTime yearTo,
			@Param("startedFrom") LocalDateTime startedFrom, @Param("startedTo") LocalDateTime startedTo,
			@Param("finishedFrom") LocalDateTime finishedFrom, @Param("finishedTo") LocalDateTime finishedTo,
			@Param("cursor") Long cursor,
			Pageable pageable);

	/**
	 * 그 해에 다 읽은 책 수. 달성률의 분자이고, 서재 조회의 연도 필터와 무관하게
	 * <b>완독 기준으로만</b> 센다. 여기도 범위로 물어 인덱스를 막지 않는다.
	 */
	@Query("""
			select count(r) from Reading r
			where r.userId = :userId
			  and r.status = com.irene.twelvebooks.reading.ReadingStatus.FINISHED
			  and r.finishedAt >= :from and r.finishedAt < :to
			""")
	long countFinishedBetween(@Param("userId") Long userId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
