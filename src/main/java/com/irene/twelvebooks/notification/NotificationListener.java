package com.irene.twelvebooks.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 반응이 커밋된 <b>뒤에</b> 알림을 만든다.
 *
 * <p>같은 트랜잭션에서 만들면 알림 저장이 실패했을 때 좋아요까지 롤백된다. 부가 기능이 본
 * 기능을 되돌리면 안 된다 — 사용자는 하트를 눌렀는데 눌리지 않은 화면을 보게 된다.
 *
 * <p>{@code @Async}로 스레드를 나누는 데에는 이유가 있다. 같은 스레드에서 새 트랜잭션을 열면
 * <b>커밋 시점에 커넥션이 둘 필요하다</b> — 바깥 트랜잭션이 아직 자기 것을 쥔 채 콜백이 돈다.
 * 동시 요청이 풀 크기에 가까워지면 전부 두 번째 커넥션을 기다리며 멈춘다. 실제로 동시 좋아요
 * 10건에서 그렇게 됐다. 트랜잭션은 {@link NotificationService#notify}가 연다.
 *
 * <p>대가는 알림이 <b>조금 늦게</b> 생긴다는 것이다. 요청이 200을 돌려준 직후에는 아직 없을
 * 수 있다. 알림에는 받아들일 만한 지연이고, 그 대신 반응 자체가 절대 느려지지 않는다.
 */
@Component
public class NotificationListener {

	private final NotificationService notificationService;

	public NotificationListener(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@Async
	@TransactionalEventListener
	public void on(ReactionEvents.PostLiked event) {
		notificationService.notify(event.authorId(), event.actorId(), NotificationType.POST_LIKED,
				NotificationTarget.POST, event.postId());
	}

	@Async
	@TransactionalEventListener
	public void on(ReactionEvents.PostCommented event) {
		notificationService.notify(event.authorId(), event.actorId(), NotificationType.POST_COMMENTED,
				NotificationTarget.POST, event.postId());
	}

	@Async
	@TransactionalEventListener
	public void on(ReactionEvents.Followed event) {
		// 대상을 팔로우당한 본인으로 둔다. 비워 두면 유니크 제약이 발동하지 않아
		// 팔로우를 껐다 켤 때마다 알림이 쌓인다.
		notificationService.notify(event.followeeId(), event.followerId(), NotificationType.FOLLOWED,
				NotificationTarget.USER, event.followeeId());
	}
}
