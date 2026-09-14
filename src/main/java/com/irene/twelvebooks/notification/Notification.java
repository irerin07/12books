package com.irene.twelvebooks.notification;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 누가 나에게 무엇을 했는지의 기록.
 *
 * <p><b>취소해도 지우지 않는다.</b> 좋아요를 껐다고 해서 "그때 좋아요를 받았다"는 사실이
 * 없어지지는 않는다. 알림을 지우면 이미 읽은 것이 목록에서 사라져 사용자가 무엇을 봤는지
 * 잃는다.
 *
 * <p>같은 일이 반복돼도 한 건만 남는다 — 유니크 제약이 1차 방어선이고, 위반은 오류가 아니라
 * "이미 알렸다"는 뜻이라 호출부가 삼킨다.
 */
@Entity
@Table(name = "notifications")
public class Notification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "recipient_id", nullable = false)
	private Long recipientId;

	@Column(name = "actor_id", nullable = false)
	private Long actorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", length = 20)
	private NotificationTarget targetType;

	@Column(name = "target_id")
	private Long targetId;

	/** 읽은 시각. {@code null}이면 안 읽었다. */
	@Column(name = "read_at")
	private LocalDateTime readAt;

	protected Notification() {
	}

	/**
	 * @throws IllegalArgumentException 받는 사람과 한 사람이 같을 때. 본인 행동은 알리지 않는데,
	 *                                  호출부가 걸러야 할 것을 엔티티도 스스로 지킨다.
	 */
	public static Notification of(Long recipientId, Long actorId, NotificationType type,
			NotificationTarget targetType, Long targetId) {
		if (recipientId.equals(actorId)) {
			throw new IllegalArgumentException("자기 자신에게는 알리지 않습니다");
		}
		Notification notification = new Notification();
		notification.recipientId = recipientId;
		notification.actorId = actorId;
		notification.type = type;
		notification.targetType = targetType;
		notification.targetId = targetId;
		return notification;
	}

	public boolean receivedBy(Long candidateUserId) {
		return recipientId.equals(candidateUserId);
	}

	public Long getId() {
		return id;
	}

	public Long getRecipientId() {
		return recipientId;
	}

	public Long getActorId() {
		return actorId;
	}

	public NotificationType getType() {
		return type;
	}

	public NotificationTarget getTargetType() {
		return targetType;
	}

	public Long getTargetId() {
		return targetId;
	}

	public LocalDateTime getReadAt() {
		return readAt;
	}

	public boolean isRead() {
		return readAt != null;
	}
}
