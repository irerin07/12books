package com.irene.twelvebooks.post;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.notification.ReactionEvents;
import org.springframework.context.ApplicationEventPublisher;
import com.irene.twelvebooks.post.dto.CommentCreateRequest;
import com.irene.twelvebooks.post.dto.CommentResponse;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommentService {

	private static final Logger log = LoggerFactory.getLogger(CommentService.class);

	private final CommentRepository commentRepository;
	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	public CommentService(CommentRepository commentRepository, PostRepository postRepository,
			UserRepository userRepository, Clock clock, ApplicationEventPublisher events) {
		this.commentRepository = commentRepository;
		this.postRepository = postRepository;
		this.userRepository = userRepository;
		this.clock = clock;
		this.events = events;
	}

	/** 댓글을 저장한다. 댓글 수는 조회 시 계산하므로 부모 글을 갱신하지 않는다. */
	@Transactional
	public CommentResponse write(Long authorId, Long postId, CommentCreateRequest request) {
		Long postAuthorId = postRepository.findLiveAuthorId(postId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
		User author = userRepository.findById(authorId)
				// 남은 access 토큰으로 탈퇴 후 새 댓글을 작성할 수는 없다.
				.filter(candidate -> !candidate.isWithdrawn())
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

		Comment comment;
		try {
			comment = Comment.write(postId, authorId, request.content());
		}
		catch (IllegalArgumentException e) {
			// DTO 검증이 이미 같은 규칙을 보지만 엔티티도 스스로를 지킨다. 여기 걸렸다면
			// 클라이언트 입력 문제이므로 500이 아니라 400이다.
			log.debug("댓글이 불변식에 걸렸습니다: postId={}", postId, e);
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		CommentResponse response = CommentResponse.of(commentRepository.save(comment), author);
		// 좋아요와 같은 이유로 커밋 뒤에 알린다.
		events.publishEvent(new ReactionEvents.PostCommented(postId, postAuthorId, authorId));
		return response;
	}

	/** 댓글 작성자 또는 게시글 작성자만 삭제할 수 있다. */
	@Transactional
	public void remove(Long userId, Long commentId) {
		Comment comment = commentRepository.findLive(commentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
		if (!comment.writtenBy(userId) && !postAuthorIs(comment.getPostId(), userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (!postRepository.existsLive(comment.getPostId())) {
			throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
		}
		commentRepository.softDelete(commentId, LocalDateTime.now(clock));
	}

	/**
	 * 글에 달린 댓글 한 페이지.
	 *
	 * <p>작성자는 <b>페이지 전체를 모아 한 번</b> 조회한다. 댓글마다 따로 읽으면 쿼리가 목록
	 * 크기만큼 늘어난다 — 감상평 목록과 같은 방식이다.
	 */
	@Transactional(readOnly = true)
	public CursorPage<CommentResponse> byPost(Long postId, Long viewerId, Long cursor, int size) {
		if (!postRepository.existsLive(postId)) {
			// 빈 목록으로 답하면 "댓글이 없는 글"과 "없는 글"이 구분되지 않는다.
			throw new BusinessException(ErrorCode.POST_NOT_FOUND);
		}
		CursorPage<Comment> page = CursorPage.of(
				commentRepository.findPostPage(postId, viewerId, cursor, PageRequest.ofSize(size + 1)),
				size, Comment::getId);

		Map<Long, User> authors = userRepository.findAllById(
						page.items().stream().map(Comment::getAuthorId).distinct().toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));

		return new CursorPage<>(
				page.items().stream()
						.map(comment -> CommentResponse.of(comment, authors.get(comment.getAuthorId())))
						.toList(),
				page.nextCursor(), page.hasNext());
	}

	private boolean postAuthorIs(Long postId, Long userId) {
		return postRepository.findLive(postId)
				.map(post -> post.writtenBy(userId))
				.orElse(false);
	}
}
