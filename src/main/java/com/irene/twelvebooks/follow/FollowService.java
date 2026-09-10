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
	 *
	 * <p>지운 행 수를 보지 않는 것은 그래서다. 0행은 실패가 아니라 이미 그 상태라는 뜻이다.
	 */
	@Transactional
	public void unfollow(Long followerId, String targetHandle) {
		followRepository.deleteRelation(followerId, idOf(targetHandle));
	}

	/** 타임라인이 매 요청 필요로 하는 목록. 팬아웃 없이 이 id들을 그대로 조건에 넣는다. */
	@Transactional(readOnly = true)
	public List<Long> followeeIds(Long userId) {
		return followRepository.findFolloweeIds(userId);
	}

	/**
	 * 보는 사람이 대상을 팔로우 중인지.
	 *
	 * <p>같은 프로필이라도 <b>보는 사람에 따라 답이 다르다.</b> 자기 자신은 팔로우할 수 없으므로
	 * 내 프로필에서는 항상 거짓이고, 화면은 그때 팔로우 버튼 대신 프로필 수정을 보여주면 된다.
	 */
	@Transactional(readOnly = true)
	public boolean isFollowing(Long viewerId, Long targetId) {
		return followRepository.existsByFollowerIdAndFolloweeId(viewerId, targetId);
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
		return page(followRepository.findFollowerPage(idOf(handle), cursor, PageRequest.ofSize(size + 1)),
				size, Follow::getFollowerId);
	}

	@Transactional(readOnly = true)
	public CursorPage<UserSummaryResponse> followings(String handle, Long cursor, int size) {
		return page(followRepository.findFolloweePage(idOf(handle), cursor, PageRequest.ofSize(size + 1)),
				size, Follow::getFolloweeId);
	}

	/**
	 * 관계 한 페이지를 사람 목록으로 바꾼다. 커서는 관계의 id이고, 화면에 나갈 사람은
	 * 방향에 따라 반대쪽이다 — 팔로워 목록은 follower, 팔로잉 목록은 followee.
	 *
	 * <p>사람은 페이지 전체를 모아 <b>한 번에</b> 조회한다. 항목마다 따로 읽으면 페이지 크기만큼
	 * 쿼리가 늘어난다. 이 방식은 목록이 20건이든 50건이든 쿼리가 둘이다.
	 */
	private CursorPage<UserSummaryResponse> page(List<Follow> rows, int size,
			Function<Follow, Long> counterpart) {
		CursorPage<Follow> follows = CursorPage.of(rows, size, Follow::getId);
		Map<Long, User> users = userRepository.findAllById(
						follows.items().stream().map(counterpart).toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));

		return new CursorPage<>(
				follows.items().stream()
						.map(follow -> UserSummaryResponse.from(users.get(counterpart.apply(follow))))
						.toList(),
				follows.nextCursor(), follows.hasNext());
	}

	private Long idOf(String handle) {
		return userRepository.findByHandle(handle)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
				.getId();
	}
}
