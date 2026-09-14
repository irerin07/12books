package com.irene.twelvebooks.report;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.common.ratelimit.RateLimit;
import com.irene.twelvebooks.report.dto.ReportCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 접수. 세 대상이 각자의 자원 아래에 붙는다.
 *
 * <p>응답에 본문이 없다. 신고자가 받을 것은 "접수됐다"뿐이고, <b>그 뒤에 무엇을 했는지는
 * 알려주지 않는다</b> — 처리 결과를 보여주면 누가 누구를 신고했는지가 역으로 드러난다.
 *
 * <p>한도를 거는 이유는 좋아요·팔로우와 같다. 신고는 남을 목록에 올리는 쓰기라 자동화되면
 * 운영자의 화면이 막힌다.
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@RateLimit(name = "report", limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
	@PostMapping("/posts/{postId}/reports")
	public ResponseEntity<Void> reportPost(@AuthUser Long userId, @PathVariable Long postId,
			@Valid @RequestBody ReportCreateRequest request) {
		reportService.reportPost(userId, postId, request);
		return ResponseEntity.noContent().build();
	}

	@RateLimit(name = "report", limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
	@PostMapping("/comments/{commentId}/reports")
	public ResponseEntity<Void> reportComment(@AuthUser Long userId, @PathVariable Long commentId,
			@Valid @RequestBody ReportCreateRequest request) {
		reportService.reportComment(userId, commentId, request);
		return ResponseEntity.noContent().build();
	}

	@RateLimit(name = "report", limit = 30, windowSeconds = 60, scope = RateLimit.Scope.USER)
	@PostMapping("/users/{handle}/reports")
	public ResponseEntity<Void> reportUser(@AuthUser Long userId, @PathVariable String handle,
			@Valid @RequestBody ReportCreateRequest request) {
		reportService.reportUser(userId, handle, request);
		return ResponseEntity.noContent().build();
	}
}
