package com.irene.twelvebooks.block;

import com.irene.twelvebooks.block.dto.BlockItemResponse;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.follow.FollowRepository;
import com.irene.twelvebooks.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BlockService {

	private final BlockRepository blockRepository;
	private final FollowRepository followRepository;
	private final UserRepository userRepository;

	public BlockService(BlockRepository blockRepository, FollowRepository followRepository,
			UserRepository userRepository) {
		this.blockRepository = blockRepository;
		this.followRepository = followRepository;
		this.userRepository = userRepository;
	}

	/**
	 * 차단한다.
	 *
	 * <p>중복은 유니크 제약이 1차 방어선이다({@code uk(blocker_id, blocked_id)}). 사전 조회만으로는
	 * 동시에 들어온 두 요청이 함께 "없음"을 보고 둘 다 통과한다. 제약 위반을 409로 바꿔 던지기만
	 * 하므로 트랜잭션이 rollback-only가 되는 것은 문제가 되지 않는다.
	 *
	 * <p><b>내가 건 팔로우만 끊는다.</b> 상대가 나를 팔로우하는 것은 상대의 의사이고, 차단은
	 * 그것을 지울 근거가 아니다 — 남은 관계는 조회에서 가려지고 차단을 풀면 그대로 돌아온다.
	 * 반대로 내 팔로우를 남겨 두면 "내 팔로잉 목록에 차단한 사람이 있는" 상태가 된다.
	 */
	@Transactional
	public void block(Long blockerId, String targetHandle) {
		Long blockedId = idOf(targetHandle);
		try {
			blockRepository.saveAndFlush(Block.of(blockerId, blockedId));
		}
		catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.SELF_BLOCK_NOT_ALLOWED);
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
		}
		followRepository.deleteRelation(blockerId, blockedId);
	}

	/** 차단을 푼다. 차단돼 있지 않아도 204다 — 결과가 같으므로 오류로 만들 이유가 없다. */
	@Transactional
	public void unblock(Long blockerId, String targetHandle) {
		blockRepository.unblock(blockerId, idOf(targetHandle));
	}

	/** 내가 차단한 사람 목록. 끊긴 팔로우는 돌려주지 않는다 — 차단을 풀어도 다시 걸어야 한다. */
	@Transactional(readOnly = true)
	public CursorPage<BlockItemResponse> blocked(Long blockerId, Long cursor, int size) {
		List<BlockItemResponse> rows =
				blockRepository.findBlockedPage(blockerId, cursor, PageRequest.ofSize(size + 1));
		return CursorPage.of(rows, size, BlockItemResponse::id);
	}

	private Long idOf(String handle) {
		return userRepository.findByHandle(handle)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
				.getId();
	}
}
