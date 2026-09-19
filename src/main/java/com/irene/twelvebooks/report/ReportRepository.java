package com.irene.twelvebooks.report;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
	 * 이 대상에 남아 있는 <b>다른</b> 인정된 신고. 공개 요청을 받아들일지 판정한다.
	 *
	 * <p><b>잠금 읽기인 것이 요점이다.</b> REPEATABLE READ에서 평범한 조회는 트랜잭션이
	 * 시작할 때의 스냅숏을 보므로, 그 사이 다른 운영자가 인정하고 커밋한 신고를 놓친다 —
	 * 그러면 인정된 신고가 있는데도 글이 열린다. 잠금 읽기는 최신 커밋을 본다.
	 *
	 * <p>호출 전에 <b>대상 행을 먼저 잠근다.</b> 여기서 {@code reports}를 먼저 잠그면
	 * {@code posts}와 순서가 엇갈려 교착에 빠진다(그 사고가 실제로 있었다).
	 *
	 * <p>자기 자신은 뺀다. 지금 처리 중인 신고는 아직 옛 상태를 들고 있어, 빼지 않으면
	 * 자기가 자기를 막는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select r.id from Report r
			where r.targetType = :targetType
			  and r.targetId = :targetId
			  and r.status = com.irene.twelvebooks.report.ReportStatus.ACTIONED
			  and r.id <> :exceptId
			""")
	List<Long> lockOtherActionedIds(@Param("targetType") ReportTarget targetType,
			@Param("targetId") Long targetId, @Param("exceptId") Long exceptId);

}
