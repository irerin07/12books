package com.irene.twelvebooks.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.report.Report;
import com.irene.twelvebooks.report.ReportReason;
import com.irene.twelvebooks.report.ReportStatus;
import com.irene.twelvebooks.report.ReportTarget;

import java.time.LocalDateTime;

/**
 * 운영자가 보는 신고 한 줄.
 *
 * <p>신고당한 <b>내용을 함께 싣는다.</b> id만 주면 운영자가 무엇을 내릴지 판단할 수 없고,
 * 이미 내린 글은 일반 조회로 열리지도 않는다.
 *
 * <p>{@code targetContent}는 글·댓글일 때만, {@code targetHandle}은 사람일 때만 있다.
 * 모르는 값은 {@code null}로 싣지 않고 키째 뺀다(CLAUDE.md 빈 값 규약).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminReportResponse(
		Long id,
		ReportStatus status,
		ReportReason reason,
		String detail,
		ReportTarget targetType,
		Long targetId,
		String targetContent,
		String targetHandle,
		String reporterHandle,
		LocalDateTime createdAt,
		LocalDateTime handledAt) {

	public static AdminReportResponse of(Report report, String targetContent, String targetHandle,
			String reporterHandle) {
		return new AdminReportResponse(report.getId(), report.getStatus(), report.getReason(),
				report.getDetail(), report.getTargetType(), report.getTargetId(),
				targetContent, targetHandle, reporterHandle,
				report.getCreatedAt(), report.getHandledAt());
	}
}
