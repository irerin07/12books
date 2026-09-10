package com.irene.twelvebooks.follow;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * 한 사람이 다른 사람을 팔로우한다. 관계는 <b>단방향</b>이다 — 맞팔은 두 행이다.
 *
 * <p>복합 PK라 대리 키가 없다. 중복 팔로우를 막는 것이 이 키의 일이고, 응용이 먼저 조회해서
 * 막는 대신 DB가 1차 방어선이 된다.
 *
 * <p>{@link Persistable}을 구현하는 이유가 바로 그 방어선을 살리기 위해서다. 식별자를 우리가
 * 정해서 넣으므로 Spring Data는 이 엔티티를 <b>이미 있는 것</b>으로 보고 {@code save()}를 merge로
 * 처리한다 — 그러면 select 후 update가 나가 중복 팔로우가 조용히 성공해 버린다. "새 것"이라고
 * 알려 줘야 insert가 나가고, 그제야 복합 PK가 중복을 잡아낸다.
 */
@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
public class Follow extends BaseTimeEntity implements Persistable<FollowId> {

	@Id
	@Column(name = "follower_id")
	private Long followerId;

	@Id
	@Column(name = "followee_id")
	private Long followeeId;

	/** 저장·조회를 거치지 않은 인스턴스만 새 것이다. 컬럼이 아니므로 테이블에는 남지 않는다. */
	@Transient
	private boolean isNew = true;

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

	@Override
	public FollowId getId() {
		return new FollowId(followerId, followeeId);
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	public Long getFollowerId() {
		return followerId;
	}

	public Long getFolloweeId() {
		return followeeId;
	}
}
