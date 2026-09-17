package com.irene.twelvebooks.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.notification.Notification;
import com.irene.twelvebooks.notification.NotificationType;
import com.irene.twelvebooks.notification.ActorView;
import com.irene.twelvebooks.user.dto.UserSummaryResponse;

import java.time.LocalDateTime;

/**
 * 알림 한 건.
 *
 * <p>탈퇴한 행위자는 actor를 생략한다. 알림 자체는 남기며 탈퇴한 프로필은 노출하지 않는다.
 *
 * <p>{@code post}는 팔로우 알림에서 빠진다. 딸린 글이 없는데 빈 객체를 실으면 화면이
 * "글이 있는데 내용이 없다"와 구분할 수 없다.
 *
 * @param post 글 본문을 통째로 싣는다. 감상평이 1000자 이하라 잘라 보낼 만큼 크지 않고,
 *             자르는 길이는 화면마다 달라서 서버가 정할 일이 아니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(Long id, NotificationType type, UserSummaryResponse actor,
		PostBrief post, boolean read, LocalDateTime createdAt) {

	public record PostBrief(Long id, String content) {
	}

	public static NotificationResponse of(Notification notification, ActorView actor, PostBrief post) {
		return new NotificationResponse(notification.getId(), notification.getType(),
				actor == null ? null : new UserSummaryResponse(actor.handle(), actor.displayName(), actor.avatarUrl()),
				post, notification.isRead(), notification.getCreatedAt());
	}
}
