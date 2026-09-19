package com.irene.twelvebooks.report;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.post.CommentRepository;
import com.irene.twelvebooks.post.PostRepository;
import com.irene.twelvebooks.report.dto.AdminReportResponse;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import com.irene.twelvebooks.user.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 쌓인 신고를 운영자가 보고 처리한다.
 *
 * <p>권한은 <b>매 요청 DB에서 확인한다.</b> 토큰에 역할을 실으면 권한을 뺏어도 그 사람의
 * 토큰이 만료될 때까지 관리자로 남는다 — 뺏는 이유를 생각하면 그 시차가 가장 곤란하다.
 * 조회 한 번의 비용은 운영자 경로에서만 든다.
 */
@Service
public class ReportAdminService {

	private final ReportRepository reportRepository;
	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public ReportAdminService(ReportRepository reportRepository, PostRepository postRepository,
			CommentRepository commentRepository, UserRepository userRepository, Clock clock) {
		this.reportRepository = reportRepository;
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/**
	 * 신고 목록. 상태를 주면 그것만, 안 주면 전부.
	 *
	 * <p>신고당한 내용은 <b>페이지 전체를 모아 종류별로 한 번씩</b> 읽는다. 줄마다 따로 읽으면
	 * 페이지 크기만큼 쿼리가 늘고, 운영자 화면은 한 번에 많이 본다.
	 */
	@Transactional(readOnly = true)
	public CursorPage<AdminReportResponse> list(Long adminId, ReportStatus status, Long cursor, int size) {
		requireAdmin(adminId);
		CursorPage<Report> page = CursorPage.of(
				reportRepository.findPage(status, cursor, PageRequest.ofSize(size + 1)),
				size, Report::getId);

		Map<Long, String> posts = contentsOf(ReportTarget.POST, page.items(),
				postRepository::findAnyPostViews);
		Map<Long, String> comments = contentsOf(ReportTarget.COMMENT, page.items(),
				commentRepository::findAnyCommentViews);
		Map<Long, User> people = peopleIn(page.items());

		return new CursorPage<>(page.items().stream()
				.map(report -> AdminReportResponse.of(report,
						contentOf(report, posts, comments),
						report.getTargetType() == ReportTarget.USER
								? handleOf(people.get(report.getTargetId())) : null,
						handleOf(people.get(report.getReporterId()))))
				.toList(), page.nextCursor(), page.hasNext());
	}

	/**
	 * 판단을 남기고 그대로 반영한다.
	 *
	 * <p>{@code ACTIONED}는 내리고 {@code REJECTED}는 되돌린다 — 되돌리는 쪽이 없으면 잘못
	 * 내린 것을 운영자가 DB를 직접 만져 고치게 된다.
	 *
	 * <p>사람 신고는 <b>기록만</b> 남는다. 계정을 멈추는 일은 탈퇴·차단(L2)과 함께 정해야 하고,
	 * 그 전에 급하면 그 사람의 글을 개별로 내릴 수 있다.
	 */
	@Transactional
	public void handle(Long adminId, Long reportId, ReportStatus decision, Boolean restore) {
		requireAdmin(adminId);
		Report report = reportRepository.findById(reportId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
		if (decision == ReportStatus.PENDING) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		if (decision == ReportStatus.ACTIONED) {
			hide(report);
		}
		else {
			restore(report, restore);
		}
		report.handle(decision, adminId, LocalDateTime.now(clock));
	}

	private void hide(Report report) {
		switch (report.getTargetType()) {
			case POST -> postRepository.hide(report.getTargetId(), LocalDateTime.now(clock));
			case COMMENT -> commentRepository.hide(report.getTargetId(), LocalDateTime.now(clock));
			case USER -> {
				// 계정을 멈추는 일은 아직 없다. 판단만 남는다.
			}
		}
	}

	/**
	 * 기각한다 — 대상을 다시 공개할지는 <b>요청이 밝힌다.</b>
	 *
	 * <p>"이 신고의 주장은 타당하지 않다"와 "이 글을 다시 공개한다"는 다른 판단이다. 신고가
	 * 여럿 달린 글에서 하나를 기각하는 것은 흔한 일이고, 그때마다 글이 열리면 운영자가
	 * 의도하지 않은 공개가 된다. 그렇다고 판단만 저장하고 공개를 따로 묻는 식이면 "기각했는데
	 * 글이 그대로네"를 뒤늦게 발견한다. 그래서 한 요청으로 받되 서버가 짐작하지 않는다.
	 *
	 * <p><b>다른 인정된 신고가 남아 있으면 거절한다.</b> 조용히 넘기면 운영자는 눌렀는데 아무
	 * 일도 일어나지 않은 화면을 본다. 모르는 사실을 알려 주고 그것부터 처리하게 한다.
	 *
	 * <p>사람 신고는 내린 콘텐츠가 없어 고를 것이 없다. 묻지 않고 판단만 남긴다.
	 */
	private void restore(Report report, Boolean restore) {
		if (report.getTargetType() == ReportTarget.USER) {
			return;
		}
		if (restore == null) {
			throw new BusinessException(ErrorCode.RESTORE_CHOICE_REQUIRED);
		}
		if (!restore) {
			return;
		}

		// 대상을 먼저 잠근다. 아래 조회가 reports를 잠그므로 순서를 posts → reports로
		// 고정해야 교착이 없다. 잠금을 쥔 채로 읽어야 다른 운영자가 방금 인정한 신고를 본다.
		lockTarget(report);
		if (!reportRepository.lockOtherActionedIds(report.getTargetType(), report.getTargetId(),
				report.getId()).isEmpty()) {
			throw new BusinessException(ErrorCode.OTHER_ACTIONED_REPORTS_REMAIN);
		}
		unhideTarget(report);
	}

	private void lockTarget(Report report) {
		switch (report.getTargetType()) {
			case POST -> postRepository.lockForModeration(report.getTargetId());
			case COMMENT -> commentRepository.lockForModeration(report.getTargetId());
			case USER -> {
			}
		}
	}

	/**
	 * 조건을 UPDATE 안에 그대로 둔다. 위에서 이미 확인했지만, 그 확인과 이 쓰기 사이를
	 * 한 번 더 좁히는 값이 싸다 — 판정이 문장 밖에 있으면 언제든 다시 벌어진다.
	 */
	private void unhideTarget(Report report) {
		switch (report.getTargetType()) {
			case POST -> postRepository.unhideIfLastActionedReport(
					report.getTargetId(), report.getId());
			case COMMENT -> commentRepository.unhideIfLastActionedReport(
					report.getTargetId(), report.getId());
			case USER -> {
			}
		}
	}

	private String contentOf(Report report, Map<Long, String> posts, Map<Long, String> comments) {
		return switch (report.getTargetType()) {
			case POST -> posts.get(report.getTargetId());
			case COMMENT -> comments.get(report.getTargetId());
			case USER -> null;
		};
	}

	/** 신고자와, 대상이 사람인 신고의 그 사람. 한 번에 읽는다. */
	private Map<Long, User> peopleIn(List<Report> reports) {
		List<Long> ids = reports.stream()
				.flatMap(report -> report.getTargetType() == ReportTarget.USER
						? Stream.of(report.getReporterId(), report.getTargetId())
						: Stream.of(report.getReporterId()))
				.distinct().toList();
		return userRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
	}

	private Map<Long, String> contentsOf(ReportTarget type, List<Report> reports,
			Function<List<Long>, List<ContentView>> loader) {
		List<Long> ids = reports.stream()
				.filter(report -> report.getTargetType() == type)
				.map(Report::getTargetId).distinct().toList();
		if (ids.isEmpty()) {
			// 빈 목록을 IN에 넣으면 DB마다 다르게 군다. 물어볼 것이 없으면 묻지 않는다.
			return Map.of();
		}
		return loader.apply(ids).stream()
				.collect(Collectors.toMap(ContentView::id, ContentView::content));
	}

	private String handleOf(User user) {
		return user == null ? null : user.getHandle();
	}

	/**
	 * 운영자가 아니면 403이다. 404가 아닌 이유는 <b>이 경로의 존재 자체는 비밀이 아니기</b>
	 * 때문이다 — 주소를 숨겨서 얻는 안전은 없고, 없는 척하면 권한을 잃은 운영자가 장애로 읽는다.
	 */
	private void requireAdmin(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
		if (user.getRole() != UserRole.ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}
}
