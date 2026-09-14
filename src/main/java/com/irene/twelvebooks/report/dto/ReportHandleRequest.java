package com.irene.twelvebooks.report.dto;

import com.irene.twelvebooks.report.ReportStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 운영자의 판단. {@code ACTIONED}면 대상을 내리고, {@code REJECTED}면 내렸던 것을 되돌린다.
 *
 * <p>{@code PENDING}은 받지 않는다 — "아직 안 봤다"로 되돌리는 것은 판단이 아니다.
 */
public record ReportHandleRequest(
		@NotNull ReportStatus status) {
}
