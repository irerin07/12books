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

	/**
	 * 이 대상에 <b>다른</b> 인정된 신고가 남아 있는가.
	 *
	 * <p>기각은 그 신고에 대한 판단이지 대상 전체를 열라는 뜻이 아니다. 욕설로 내린 글을
	 * "스포일러는 아니다"라는 판단 하나로 다시 공개하면, 인정된 신고가 그대로 남아 있는데도
	 * 글이 돌아온다.
	 *
	 * <p>자기 자신은 뺀다. 지금 처리 중인 신고는 아직 옛 상태를 들고 있어, 빼지 않으면
	 * <b>자기가 자기를 막아</b> 마지막 하나를 기각해도 열리지 않는다.
	 */
	@Query("""
			select count(r) > 0 from Report r
			where r.targetType = :targetType
			  and r.targetId = :targetId
			  and r.status = :status
			  and r.id <> :exceptId
			""")
	boolean existsOtherWithStatus(@Param("targetType") ReportTarget targetType,
			@Param("targetId") Long targetId, @Param("status") ReportStatus status,
			@Param("exceptId") Long exceptId);
}
