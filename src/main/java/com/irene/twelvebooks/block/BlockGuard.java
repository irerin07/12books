package com.irene.twelvebooks.block;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차단된 사이에서는 상대가 <b>없는 사람</b>이다.
 *
 * <p>목록 안의 사람만 거르고 프로필을 열어 두면 반쪽이 된다 — 글은 안 보이는데 팔로워 수는
 * 보이고, 링크를 타면 그 사람의 목록이 그대로 열린다. 그래서 handle로 사람을 집는 경로는
 * 전부 여기를 지난다.
 *
 * <p><b>404인 것이 의도다.</b> 403으로 답하면 "차단당했다"가 드러난다. 차단은 상대에게
 * 알리지 않는 것이 기본이고, 없는 사람과 같은 답을 주면 구별되지 않는다.
 *
 * <p>한 곳에 모은 이유는 자리마다 손으로 달면 언젠가 하나를 빠뜨리기 때문이다. 빠뜨려도
 * 아무도 모르는 종류의 실수라 {@code BlockConventionTest}가 함께 지킨다.
 */
@Component
public class BlockGuard {

	private final BlockRepository blockRepository;
	private final UserRepository userRepository;

	public BlockGuard(BlockRepository blockRepository, UserRepository userRepository) {
		this.blockRepository = blockRepository;
		this.userRepository = userRepository;
	}

	/**
	 * handle로 집는 경로가 부르는 쪽. 없는 사람과 차단된 사람이 <b>같은 답</b>을 받는다.
	 *
	 * <p>차단은 {@code BlockController}에서만 예외다 — 차단을 풀려면 상대를 집을 수 있어야 한다.
	 */
	@Transactional(readOnly = true)
	public void requireVisible(Long viewerId, String handle) {
		requireVisible(viewerId, userRepository.findByHandle(handle)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
				.getId());
	}

	/** 차단된 사이면 없는 사람으로 답한다. 자기 자신은 언제나 보인다. */
	@Transactional(readOnly = true)
	public void requireVisible(Long viewerId, Long targetId) {
		if (viewerId == null || viewerId.equals(targetId)) {
			return;
		}
		if (blockRepository.existsBetween(viewerId, targetId)) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
	}
}
