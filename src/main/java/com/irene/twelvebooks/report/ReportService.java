package com.irene.twelvebooks.report;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.post.Comment;
import com.irene.twelvebooks.post.CommentRepository;
import com.irene.twelvebooks.post.Post;
import com.irene.twelvebooks.post.PostRepository;
import com.irene.twelvebooks.report.dto.ReportCreateRequest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 신고를 받는다.
 *
 * <p>세 대상이 같은 흐름을 탄다 — <b>대상이 있는지 보고, 자기 것이 아닌지 보고, 넣는다.</b>
 * 앞의 둘을 건너뛰면 운영자 목록에 열어 볼 수 없는 줄이 생기거나, 자기 글을 신고해 스스로를
 * 목록에 올리는 장난이 가능해진다.
 */
@Service
public class ReportService {

	private final ReportRepository reportRepository;
	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final UserRepository userRepository;

	public ReportService(ReportRepository reportRepository, PostRepository postRepository,
			CommentRepository commentRepository, UserRepository userRepository) {
		this.reportRepository = reportRepository;
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.userRepository = userRepository;
	}

	public void reportPost(Long reporterId, Long postId, ReportCreateRequest request) {
		Post post = postRepository.findLive(postId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
		rejectSelf(post.getAuthorId(), reporterId);
		save(reporterId, ReportTarget.POST, postId, request);
	}

	public void reportComment(Long reporterId, Long commentId, ReportCreateRequest request) {
		Comment comment = commentRepository.findLive(commentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
		rejectSelf(comment.getAuthorId(), reporterId);
		save(reporterId, ReportTarget.COMMENT, commentId, request);
	}

	public void reportUser(Long reporterId, String handle, ReportCreateRequest request) {
		User target = userRepository.findByHandle(handle)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		rejectSelf(target.getId(), reporterId);
		save(reporterId, ReportTarget.USER, target.getId(), request);
	}

	/**
	 * 자기 것은 신고할 수 없다.
	 *
	 * <p>막지 않아도 큰일이 나지는 않지만, 운영자가 볼 목록에 스스로 올린 줄이 섞인다.
	 * 자기 글이 마음에 안 들면 지우면 된다.
	 */
	private void rejectSelf(Long ownerId, Long reporterId) {
		if (ownerId.equals(reporterId)) {
			throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
		}
	}

	/**
	 * 중복은 <b>유니크 제약이 1차 방어선</b>이다(CLAUDE.md 중복 규약).
	 *
	 * <p>먼저 조회해서 있으면 스킵하는 방식은 동시에 들어온 두 요청이 둘 다 "없다"를 보고
	 * 둘 다 넣는다. 제약에 맡기면 어느 쪽이 이기든 결과가 같다.
	 */
	private void save(Long reporterId, ReportTarget targetType, Long targetId,
			ReportCreateRequest request) {
		try {
			reportRepository.saveAndFlush(
					Report.of(reporterId, targetType, targetId, request.reason(), request.detail()));
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.ALREADY_REPORTED);
		}
	}
}
