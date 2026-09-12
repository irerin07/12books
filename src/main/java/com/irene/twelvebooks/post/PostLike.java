package com.irene.twelvebooks.post;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 한 사람이 한 글에 누른 좋아요. 취소는 이 행을 지우는 것이다 — 껐다 켜는 플래그를 두면
 * "누른 적 없음"과 "눌렀다 취소함"이 같은 행에서 구분되지 않고, 유니크 제약도 두 상태를
 * 함께 막게 된다.
 *
 * <p>관계 자체는 (글, 사람)으로 유일하지만 대리 키를 둔다. 중복을 막는 일은 유니크 제약이
 * 그대로 맡는다 — 응용이 먼저 조회해서 막는 대신 DB가 1차 방어선이다.
 */
@Entity
@Table(name = "post_likes")
public class PostLike extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	protected PostLike() {
	}

	public static PostLike of(Long postId, Long userId) {
		PostLike like = new PostLike();
		like.postId = postId;
		like.userId = userId;
		return like;
	}

	public Long getId() {
		return id;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getUserId() {
		return userId;
	}
}
