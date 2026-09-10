package com.irene.twelvebooks.follow;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 한 사람이 다른 사람을 팔로우한다. 관계는 <b>단방향</b>이다 — 맞팔은 두 행이다.
 *
 * <p>관계 자체는 (누가, 누구를)로 이미 유일하지만 대리 키를 둔다. 목록이 전부 id 커서라는
 * 규약을 따르기 위해서이고, 그 덕에 팔로워 목록이 <b>최신순</b>으로 나온다. 중복을 막는 일은
 * 유니크 제약이 그대로 맡는다 — 응용이 먼저 조회해서 막는 대신 DB가 1차 방어선이다.
 */
@Entity
@Table(name = "follows")
public class Follow extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "follower_id", nullable = false)
	private Long followerId;

	@Column(name = "followee_id", nullable = false)
	private Long followeeId;

	protected Follow() {
	}

	/**
	 * 자기 자신은 팔로우할 수 없다. 스키마의 CHECK도 같은 것을 막지만, 여기서 걸리면
	 * 사용자에게 제약 위반이 아니라 뜻이 있는 오류를 돌려줄 수 있다.
	 */
	public static Follow of(Long followerId, Long followeeId) {
		if (followerId.equals(followeeId)) {
			throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다");
		}
		Follow follow = new Follow();
		follow.followerId = followerId;
		follow.followeeId = followeeId;
		return follow;
	}

	public Long getId() {
		return id;
	}

	public Long getFollowerId() {
		return followerId;
	}

	public Long getFolloweeId() {
		return followeeId;
	}
}
