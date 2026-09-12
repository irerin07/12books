package com.irene.twelvebooks.post;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class PostLikeService {

	private final PostLikeRepository postLikeRepository;
	private final PostRepository postRepository;

	public PostLikeService(PostLikeRepository postLikeRepository, PostRepository postRepository) {
		this.postLikeRepository = postLikeRepository;
		this.postRepository = postRepository;
	}

	/**
	 * 좋아요를 누른다.
	 *
	 * <p>중복은 유니크 제약이 1차 방어선이다. 사전 조회만으로는 동시에 들어온 두 요청이 함께
	 * 통과한다. 여기서는 제약 위반을 409로 바꿔 던지기만 하므로 트랜잭션이 rollback-only가
	 * 되는 것이 문제가 되지 않는다 — 복구하는 게 아니라 그대로 끝내기 때문이다.
	 *
	 * <p>제약에 걸린 요청은 카운터도 올리지 못한다. 증가와 삽입이 한 트랜잭션이라 함께
	 * 되돌아간다 — 좋아요 행 없이 숫자만 오르는 상태가 생기지 않는다.
	 *
	 * <p><b>카운터를 먼저 올리고 행을 넣는다.</b> 순서를 바꾸면 교착에 빠진다: {@code post_likes}
	 * insert는 외래 키 때문에 부모인 {@code posts} 행에 공유 잠금을 잡는데, 이어지는 카운터
	 * UPDATE가 같은 행에 배타 잠금을 요구한다. 여럿이 동시에 누르면 서로 공유 잠금을 쥔 채
	 * 상대의 배타 잠금을 기다린다 — 실제로 열 명이 동시에 누르자 MySQL이 교착을 잡아 냈다.
	 * 글 행을 먼저 배타로 잡으면 승격이 없어지고, 뒤늦게 온 요청은 그냥 기다렸다가 진행한다.
	 *
	 * <p>존재 확인도 그 UPDATE가 겸한다. 앞에 {@code exists}를 두면 쿼리가 하나 늘고,
	 * 그 사이에 글이 지워지면 결국 외래 키에서 터진다.
	 */
	@Transactional
	public void like(Long userId, Long postId) {
		if (postRepository.increaseLikeCount(postId) == 0) {
			throw new BusinessException(ErrorCode.POST_NOT_FOUND);
		}
		try {
			postLikeRepository.saveAndFlush(PostLike.of(postId, userId));
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.ALREADY_LIKED);
		}
	}

	/**
	 * 좋아요를 취소한다. 누른 적이 없어도 성공으로 답한다 — 요청의 목적("이 글에 좋아요를
	 * 누르지 않은 상태")이 이미 이뤄져 있고, 두 번 눌렀다고 오류를 보여줄 이유가 없다.
	 *
	 * <p><b>지운 행이 1일 때만</b> 카운터를 내린다. 0행에도 내리면 취소를 두 번 눌러 남의
	 * 좋아요를 지울 수 있다.
	 *
	 * <p>여기는 누르기와 달리 {@code post_likes}를 먼저 만진다. 지운 행 수를 봐야 카운터를
	 * 내릴지 정할 수 있기 때문이다. 자식 행 DELETE는 부모 행에 잠금을 잡지 않아 누르기에서
	 * 났던 승격 교착이 생기지 않는다 — 그래도 누르기와 취소가 섞인 상황은
	 * {@code PostLikeConcurrentTest}가 지킨다.
	 */
	@Transactional
	public void unlike(Long userId, Long postId) {
		if (postLikeRepository.deleteLike(postId, userId) == 1) {
			postRepository.decreaseLikeCount(postId);
		}
	}

	/**
	 * 주어진 글들 중 보는 사람이 좋아요를 누른 글. 목록 한 쪽을 그릴 때 쓴다 —
	 * 글마다 묻지 않고 <b>그 페이지에 실린 id만</b> 한 번에 묻는다.
	 */
	@Transactional(readOnly = true)
	public Set<Long> likedAmong(Long viewerId, List<Long> postIds) {
		return postIds.isEmpty() ? Set.of()
				: Set.copyOf(postLikeRepository.findLikedAmong(viewerId, postIds));
	}
}
