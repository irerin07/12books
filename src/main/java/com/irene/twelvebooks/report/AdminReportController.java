package com.irene.twelvebooks.report;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.common.support.PageSize;
import com.irene.twelvebooks.report.dto.AdminReportResponse;
import com.irene.twelvebooks.report.dto.ReportHandleRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 전용. 권한 확인은 {@link ReportAdminService}가 한다 — 경로를 나누는 것만으로는
 * 아무것도 막히지 않는다.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

	private final ReportAdminService reportAdminService;

	public AdminReportController(ReportAdminService reportAdminService) {
		this.reportAdminService = reportAdminService;
	}

	@GetMapping
	public CursorPage<AdminReportResponse> list(@AuthUser Long userId,
			@RequestParam(required = false) ReportStatus status,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return reportAdminService.list(userId, status, cursor, PageSize.clamp(size));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<Void> handle(@AuthUser Long userId, @PathVariable Long id,
			@Valid @RequestBody ReportHandleRequest request) {
		reportAdminService.handle(userId, id, request.status());
		return ResponseEntity.noContent().build();
	}
}
