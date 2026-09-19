package com.irene.twelvebooks.report.dto;

import com.irene.twelvebooks.report.ReportStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 운영자의 판단. {@code ACTIONED}면 대상을 내린다.
 *
 * <p>{@code PENDING}은 받지 않는다 — "아직 안 봤다"로 되돌리는 것은 판단이 아니다.
 */
public record ReportHandleRequest(
		@NotNull ReportStatus status,

		/*
		 * 기각할 때 대상을 다시 공개할지. "이 신고의 주장은 타당하지 않다"와 "이 글을 다시
		 * 공개한다"는 다른 판단이라 서버가 짐작하지 않는다 — 신고가 여럿 달린 글에서 하나를
		 * 기각하는 것은 흔한 일이고, 그때마다 열리면 의도하지 않은 공개가 된다.
		 *
		 * 내린 콘텐츠가 있는 대상(글·댓글)을 기각할 때만 본다. 사람 신고는 내린 것이 없어
		 * 고를 것도 없고, 인정({@code ACTIONED})은 언제나 내리는 것이라 마찬가지다.
		 */
		Boolean restore) {
}
