package com.irene.twelvebooks.report;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 한 사람이 한 대상에 낸 신고.
 *
 * <p>대상은 글·댓글·사람 셋이라 외래 키가 없다. 그 대신 <b>넣기 전에 응용이 존재를 확인한다</b> —
 * 없는 대상의 신고는 운영자 목록에 열어 볼 수 없는 줄을 만든다.
 */
@Entity
@Table(name = "reports")
public class Report extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reporter_id", nullable = false)
	private Long reporterId;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20)
	private ReportTarget targetType;

	@Column(name = "target_id", nullable = false)
	private Long targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportReason reason;

	@Column(length = 500)
	private String detail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportStatus status;

	@Column(name = "handled_by")
	private Long handledBy;

	@Column(name = "handled_at")
	private LocalDateTime handledAt;

	protected Report() {
	}

	public static Report of(Long reporterId, ReportTarget targetType, Long targetId,
			ReportReason reason, String detail) {
		Report report = new Report();
		report.reporterId = reporterId;
		report.targetType = targetType;
		report.targetId = targetId;
		report.reason = reason;
		report.detail = detail;
		report.status = ReportStatus.PENDING;
		return report;
	}

	/**
	 * 운영자가 판단을 남긴다.
	 *
	 * <p>처리한 신고를 다시 처리할 수 있게 열어 둔다 — 기각했다가 다시 보니 문제였던 경우가
	 * 실제로 생기고, 그때 되돌릴 방법이 없으면 운영자가 DB를 직접 만지게 된다. 누가 언제
	 * 바꿨는지는 매번 덮어써서 <b>마지막 판단</b>을 남긴다.
	 */
	public void handle(ReportStatus decision, Long handlerId, LocalDateTime now) {
		if (decision == ReportStatus.PENDING) {
			throw new IllegalArgumentException("처리 결과는 PENDING일 수 없습니다");
		}
		this.status = decision;
		this.handledBy = handlerId;
		this.handledAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getReporterId() {
		return reporterId;
	}

	public ReportTarget getTargetType() {
		return targetType;
	}

	public Long getTargetId() {
		return targetId;
	}

	public ReportReason getReason() {
		return reason;
	}

	public String getDetail() {
		return detail;
	}

	public ReportStatus getStatus() {
		return status;
	}

	public Long getHandledBy() {
		return handledBy;
	}

	public LocalDateTime getHandledAt() {
		return handledAt;
	}
}
