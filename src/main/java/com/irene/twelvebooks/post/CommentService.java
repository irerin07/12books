package com.irene.twelvebooks.post;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.post.dto.CommentCreateRequest;
import com.irene.twelvebooks.post.dto.CommentResponse;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommentService {

	private static final Logger log = LoggerFactory.getLogger(CommentService.class);

	private final CommentRepository commentRepository;
	private final PostRepository postRepository;
	private final UserRepository userRepository;

	public CommentService(CommentRepository commentRepository, PostRepository postRepository,
			UserRepository userRepository) {
		this.commentRepository = commentRepository;
		this.postRepository = postRepository;
		this.userRepository = userRepository;
	}

	/**
	 * 댓글을 단다.
	 *
	 * <p><b>카운터를 먼저 올리고 댓글 행을 넣는다.</b> 좋아요와 같은 이유다 —
	 * {@code comments} insert는 외래 키 때문에 부모인 {@code posts} 행에 공유 잠금을 잡는데,
	 * 이어지는 카운터 UPDATE가 같은 행에 배타 잠금을 요구한다. 같은 글에 여럿이 동시에 달면
	 * 서로 공유 잠금을 쥔 채 상대의 배타 잠금을 기다린다. 글 행을 먼저 배타로 잡으면
	 * 승격이 없어진다.
	 *
	 * <p>존재 확인도 그 UPDATE가 겸한다. 앞에 {@code exists}를 두면 쿼리가 하나 늘고,
	 * 그 사이에 글이 지워지면 결국 외래 키에서 터진다.
	 *
	 * <p>입력이 규칙에 어긋나 예외로 끝나도 카운터는 함께 되돌아간다. 한 트랜잭션이라
	 * 댓글 없이 숫자만 오르는 상태가 생기지 않는다.
	 */
	@Transactional
	public CommentResponse write(Long authorId, Long postId, CommentCreateRequest request) {
		if (postRepository.increaseCommentCount(postId) == 0) {
			throw new BusinessException(ErrorCode.POST_NOT_FOUND);
		}
		User author = userRepository.findById(authorId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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

		return CommentResponse.of(commentRepository.save(comment), author);
	}

	/**
	 * 댓글 삭제 권한은 <b>댓글 작성자 또는 글 작성자</b>다. 내 글 아래에 무엇이 남는지는
	 * 글쓴이도 정할 수 있어야 하는데, 신고·차단이 MVP 밖이라 지금은 이것이 유일한 수단이다.
	 *
	 * <p>남의 댓글에 404가 아니라 403을 주는 것은 감상평과 같은 이유다 — 공개된 글에 달린
	 * 공개된 댓글이라 존재 자체가 비밀이 아니다.
	 */
	@Transactional
	public void remove(Long userId, Long commentId) {
		Comment comment = commentRepository.findById(commentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
		if (!comment.writtenBy(userId) && !postAuthorIs(comment.getPostId(), userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		commentRepository.delete(comment);
		postRepository.decreaseCommentCount(comment.getPostId());
	}

	/**
	 * 글에 달린 댓글 한 페이지.
	 *
	 * <p>작성자는 <b>페이지 전체를 모아 한 번</b> 조회한다. 댓글마다 따로 읽으면 쿼리가 목록
	 * 크기만큼 늘어난다 — 감상평 목록과 같은 방식이다.
	 */
	@Transactional(readOnly = true)
	public CursorPage<CommentResponse> byPost(Long postId, Long cursor, int size) {
		if (!postRepository.existsById(postId)) {
			// 빈 목록으로 답하면 "댓글이 없는 글"과 "없는 글"이 구분되지 않는다.
			throw new BusinessException(ErrorCode.POST_NOT_FOUND);
		}
		CursorPage<Comment> page = CursorPage.of(
				commentRepository.findPostPage(postId, cursor, PageRequest.ofSize(size + 1)),
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
		return postRepository.findById(postId)
				.map(post -> post.writtenBy(userId))
				.orElse(false);
	}
}
