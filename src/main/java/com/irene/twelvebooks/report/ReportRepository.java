package com.irene.twelvebooks.report;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

	/**
	 * 운영자가 보는 한 페이지. 상태로 좁히고 최신순이다.
	 *
	 * <p>{@code status}가 {@code null}이면 상태를 가리지 않는다 — "처리한 것까지 다시 보기"가
	 * 필요하고, 그때마다 경로를 늘리는 대신 조건 하나를 비운다.
	 */
	@Query("""
			select r from Report r
			where (:status is null or r.status = :status)
			  and (:cursor is null or r.id < :cursor)
			order by r.id desc
			""")
	List<Report> findPage(@Param("status") ReportStatus status, @Param("cursor") Long cursor,
			Pageable pageable);

}
