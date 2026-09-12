package com.irene.twelvebooks.post;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 감상평에 달린 댓글. <b>1단계뿐이다</b> — 대댓글이 없으므로 {@code parentId}를 두지 않는다.
 * 쓰지 않을 필드는 부채다: 있으면 조회마다 "이건 왜 항상 비어 있나"를 설명해야 하고,
 * 언젠가 누가 채운다.
 *
 * <p>읽은 분량({@code fromPage}·{@code toPage})도 붙지 않는다. "읽은 만큼 기록한다"는 감상평의
 * 계약이고, 댓글은 그 글에 대한 말이다. 붙이려면 쪽수를 또 입력받을지, 서재의 현재 진도를
 * 박을지, 진도가 바뀌면 옛 댓글은 어떻게 할지를 함께 정해야 한다 — 실제로 필요해진 뒤에 한다.
 */
@Entity
@Table(name = "comments")
public class Comment extends BaseTimeEntity {

	private static final int MAX_CONTENT_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	/**
	 * 지운 시각. {@code null}이면 살아 있다.
	 *
	 * <p>행을 지우지 않는 이유는 {@code V7__soft_delete.sql}에 있다. 여기서는 <b>모든 조회가
	 * 이 조건을 직접 달아야 한다</b>는 점이 중요하다 — Hibernate의 {@code @SQLRestriction}으로
	 * 한 번에 거는 방법도 있지만, 그러면 어느 쿼리에 조건이 붙었는지가 보이지 않고 통계나
	 * 관리 조회에서 지운 것까지 보려 할 때 빠져나갈 구멍이 없다.
	 */
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected Comment() {
	}

	public static Comment write(Long postId, Long authorId, String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("댓글은 비어 있을 수 없습니다");
		}
		if (content.length() > MAX_CONTENT_LENGTH) {
			throw new IllegalArgumentException("댓글은 500자를 넘을 수 없습니다");
		}
		Comment comment = new Comment();
		comment.postId = postId;
		comment.authorId = authorId;
		comment.content = content;
		return comment;
	}

	public boolean writtenBy(Long candidateUserId) {
		return authorId.equals(candidateUserId);
	}

	public Long getId() {
		return id;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public String getContent() {
		return content;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}
}
