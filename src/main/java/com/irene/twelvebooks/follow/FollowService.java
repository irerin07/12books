package com.irene.twelvebooks.follow;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import com.irene.twelvebooks.user.dto.UserSummaryResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FollowService {

	private final FollowRepository followRepository;
	private final UserRepository userRepository;

	public FollowService(FollowRepository followRepository, UserRepository userRepository) {
		this.followRepository = followRepository;
		this.userRepository = userRepository;
	}

	/**
	 * 팔로우한다.
	 *
	 * <p>중복은 복합 PK가 1차 방어선이다. 사전 조회만으로는 동시에 들어온 두 요청이 함께
	 * 통과할 수 있다. 여기서는 제약 위반을 409로 바꿔 던지기만 하므로 트랜잭션이
	 * rollback-only가 되는 것이 문제가 되지 않는다 — 복구하는 게 아니라 그대로 끝내기 때문이다.
	 */
	@Transactional
	public void follow(Long followerId, String targetHandle) {
		Long followeeId = idOf(targetHandle);
		try {
			followRepository.saveAndFlush(Follow.of(followerId, followeeId));
		}
		catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.ALREADY_FOLLOWING);
		}
	}

	/**
	 * 언팔로우한다. 팔로우한 적이 없어도 성공으로 답한다 — 요청의 목적("이 사람을 팔로우하고
	 * 있지 않다")이 이미 이뤄진 상태이고, 두 번 눌렀다고 오류를 보여줄 이유가 없다.
	 */
	@Transactional
	public void unfollow(Long followerId, String targetHandle) {
		followRepository.deleteByFollowerIdAndFolloweeId(followerId, idOf(targetHandle));
	}

	/** 타임라인이 매 요청 필요로 하는 목록. 팬아웃 없이 이 id들을 그대로 조건에 넣는다. */
	@Transactional(readOnly = true)
	public List<Long> followeeIds(Long userId) {
		return followRepository.findFolloweeIds(userId);
	}

	@Transactional(readOnly = true)
	public long followerCount(Long userId) {
		return followRepository.countByFolloweeId(userId);
	}

	@Transactional(readOnly = true)
	public long followingCount(Long userId) {
		return followRepository.countByFollowerId(userId);
	}

	@Transactional(readOnly = true)
	public CursorPage<UserSummaryResponse> followers(String handle, Long cursor, int size) {
		return page(followRepository.findFollowerIdPage(idOf(handle), cursor, PageRequest.ofSize(size + 1)), size);
	}

	@Transactional(readOnly = true)
	public CursorPage<UserSummaryResponse> followings(String handle, Long cursor, int size) {
		return page(followRepository.findFolloweeIdPage(idOf(handle), cursor, PageRequest.ofSize(size + 1)), size);
	}

	/**
	 * id 목록을 사람으로 바꾼다.
	 *
	 * <p>관계 테이블에서 <b>id만</b> 읽고 사람은 한 번에 모아 조회한다. 조인으로 한 번에
	 * 가져올 수도 있지만, 이러면 관계 조회가 인덱스만 읽고 끝나 목록이 커져도 쿼리는 둘이다.
	 */
	private CursorPage<UserSummaryResponse> page(List<Long> rows, int size) {
		CursorPage<Long> ids = CursorPage.of(rows, size, Function.identity());
		Map<Long, User> users = userRepository.findAllById(ids.items()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));

		return new CursorPage<>(
				ids.items().stream().map(id -> UserSummaryResponse.from(users.get(id))).toList(),
				ids.nextCursor(), ids.hasNext());
	}

	private Long idOf(String handle) {
		return userRepository.findByHandle(handle)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
				.getId();
	}
}
