package com.irene.twelvebooks.block;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 한 사람이 다른 사람을 차단한다. <b>행은 한 방향</b>(누가 눌렀나)이고, <b>보이지 않는 것은
 * 양방향</b>이다 — 조회가 두 방향을 모두 본다.
 *
 * <p>둘을 가른 이유는 되돌릴 때다. 양방향으로 두 행을 만들면 한쪽이 풀 때 상대의 차단까지
 * 풀리거나, 누가 눌렀는지를 잃는다.
 */
@Entity
@Table(name = "blocks")
public class Block extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "blocker_id", nullable = false)
	private Long blockerId;

	@Column(name = "blocked_id", nullable = false)
	private Long blockedId;

	protected Block() {
	}

	/**
	 * 자기 자신은 차단할 수 없다. 스키마의 CHECK도 같은 것을 막지만, 여기서 걸리면
	 * 사용자에게 제약 위반이 아니라 뜻이 있는 오류를 돌려줄 수 있다.
	 */
	public static Block of(Long blockerId, Long blockedId) {
		if (blockerId.equals(blockedId)) {
			throw new IllegalArgumentException("자기 자신은 차단할 수 없습니다");
		}
		Block block = new Block();
		block.blockerId = blockerId;
		block.blockedId = blockedId;
		return block;
	}

	public Long getId() {
		return id;
	}

	public Long getBlockerId() {
		return blockerId;
	}

	public Long getBlockedId() {
		return blockedId;
	}
}
