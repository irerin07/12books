package com.irene.twelvebooks.report.dto;

import com.irene.twelvebooks.report.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 접수. 무엇을 신고하는지는 경로가 말한다.
 *
 * <p>{@code detail}은 선택이다. 필수로 만들면 사유를 고르고 나서 또 쓰라고 요구하는 셈이라
 * 신고를 포기하게 만든다.
 */
public record ReportCreateRequest(
		@NotNull ReportReason reason,
		@Size(max = 500) String detail) {
}
