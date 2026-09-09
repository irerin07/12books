package com.irene.twelvebooks.reading;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

	Optional<Reading> findByUserIdAndBookId(Long userId, Long bookId);

	/**
	 * 서재 한 페이지. 주어진 필터는 전부 AND로 묶이고, 주지 않은 것은 조건에서 빠진다.
	 *
	 * <p>{@code year}는 시작 연도와 완독 연도 중 <b>하나라도</b> 맞으면 통과하는 편의 필터다.
	 * 이것을 완독 연도로만 두면 {@code year=2026&status=READING}이 항상 빈 목록이 된다 —
	 * 읽는 중인 책은 완독일이 없어 연도 조건에 걸릴 수가 없기 때문이다.
	 *
	 * <p>정렬은 {@code id desc}라 커서가 id 하나다. {@code size + 1}건을 가져와 다음 페이지
	 * 존재를 판정한다.
	 */
	@Query("""
			select r from Reading r
			where r.userId = :userId
			  and (:status is null or r.status = :status)
			  and (:startedYear is null or year(r.startedAt) = :startedYear)
			  and (:finishedYear is null or year(r.finishedAt) = :finishedYear)
			  and (:year is null or year(r.startedAt) = :year or year(r.finishedAt) = :year)
			  and (:cursor is null or r.id < :cursor)
			order by r.id desc
			""")
	List<Reading> findLibraryPage(@Param("userId") Long userId,
			@Param("status") ReadingStatus status,
			@Param("year") Integer year,
			@Param("startedYear") Integer startedYear,
			@Param("finishedYear") Integer finishedYear,
			@Param("cursor") Long cursor,
			Pageable pageable);

	/**
	 * 그 해에 다 읽은 책 수. 달성률의 분자이고, 서재 조회의 연도 필터와 무관하게
	 * <b>완독 기준으로만</b> 센다.
	 */
	@Query("""
			select count(r) from Reading r
			where r.userId = :userId
			  and r.status = com.irene.twelvebooks.reading.ReadingStatus.FINISHED
			  and year(r.finishedAt) = :year
			""")
	long countFinishedIn(@Param("userId") Long userId, @Param("year") int year);
}
