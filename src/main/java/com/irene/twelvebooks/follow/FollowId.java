package com.irene.twelvebooks.follow;

import java.io.Serializable;
import java.util.Objects;

/**
 * 팔로우 관계의 식별자. 관계 자체가 (누가, 누구를)로 이미 유일해서 대리 키를 두지 않는다.
 */
public class FollowId implements Serializable {

	private Long followerId;
	private Long followeeId;

	protected FollowId() {
	}

	FollowId(Long followerId, Long followeeId) {
		this.followerId = followerId;
		this.followeeId = followeeId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof FollowId other)) {
			return false;
		}
		return Objects.equals(followerId, other.followerId) && Objects.equals(followeeId, other.followeeId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(followerId, followeeId);
	}
}
