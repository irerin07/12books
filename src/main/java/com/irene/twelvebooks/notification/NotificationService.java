package com.irene.twelvebooks.notification;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.notification.dto.NotificationResponse;
import com.irene.twelvebooks.post.Post;
import com.irene.twelvebooks.post.PostRepository;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final Clock clock;

	public NotificationService(NotificationRepository notificationRepository,
			UserRepository userRepository, PostRepository postRepository, Clock clock) {
		this.notificationRepository = notificationRepository;
		this.userRepository = userRepository;
		this.postRepository = postRepository;
		this.clock = clock;
	}

	/**
	 * 알림 하나를 남긴다.
	 *
	 * <p>본인 행동이면 아무것도 하지 않는다. 내 글에 내가 좋아요를 눌렀다고 나에게 알릴 이유가
	 * 없고, 스키마의 CHECK도 같은 것을 막는다.
	 *
	 * <p>같은 일이 반복되면 유니크 제약에 걸리는데 <b>그것은 오류가 아니다.</b> "이미 알렸다"는
	 * 뜻이므로 삼킨다 — 하트를 껐다 켰다 할 때마다 알림이 쌓이면 목록이 한 사람으로 도배된다.
	 *
	 * <p><b>여기에 {@code @Transactional}을 두지 않는다.</b> 두면 제약 위반을 잡아도 소용이
	 * 없다 — 예외를 삼켜도 그 트랜잭션의 rollback-only 표시는 남아서, 정상 종료한 뒤 커밋
	 * 시점에 {@code UnexpectedRollbackException}이 난다. 저장 자체가 리포지토리의 트랜잭션
	 * 안에서 끝나므로 우리는 그 <b>바깥에서</b> 잡으면 되고, 그래야 정상적인 반복 행동이
	 * ERROR 로그를 쌓지 않는다.
	 */
	public void notify(Long recipientId, Long actorId, NotificationType type,
			NotificationTarget targetType, Long targetId) {
		if (recipientId.equals(actorId)) {
			return;
		}
		try {
			notificationRepository.saveAndFlush(
					Notification.of(recipientId, actorId, type, targetType, targetId));
		}
		catch (DataIntegrityViolationException e) {
			log.debug("이미 알린 일입니다: recipient={} actor={} type={}", recipientId, actorId, type);
		}
	}

	/**
	 * 내 알림 한 페이지.
	 *
	 * <p>행위자와 글은 <b>페이지 전체를 모아 한 번씩</b> 읽는다. 알림마다 따로 읽으면 목록
	 * 크기만큼 쿼리가 늘어난다 — 감상평 목록과 같은 방식이다.
	 *
	 * <p>지워진 글에 달렸던 알림은 <b>글 정보 없이</b> 나간다. 알림은 남기고 글만 사라진
	 * 상태가 정상이고, 그때 목록이 통째로 깨지면 안 된다. 지운 본문이 여기로 새면 삭제가
	 * 삭제가 아니게 된다.
	 */
	@Transactional(readOnly = true)
	public CursorPage<NotificationResponse> list(Long userId, Long cursor, int size) {
		CursorPage<Notification> page = CursorPage.of(
				notificationRepository.findPage(userId, cursor, PageRequest.ofSize(size + 1)),
				size, Notification::getId);

		Map<Long, User> actors = userRepository.findAllById(
						page.items().stream().map(Notification::getActorId).distinct().toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));

		List<Long> postIds = page.items().stream()
				.filter(n -> n.getTargetType() == NotificationTarget.POST)
				.map(Notification::getTargetId)
				.distinct()
				.toList();
		// 지운 글은 빼고 가져온다. findAllById는 지운 글까지 돌려주고, 그것을 실으면
		// 지운 본문이 알림으로 다시 보인다.
		Map<Long, Post> posts = postIds.isEmpty() ? Map.of()
				: postRepository.findAllLive(postIds).stream()
						.collect(Collectors.toMap(Post::getId, Function.identity()));

		return new CursorPage<>(
				page.items().stream()
						.map(n -> NotificationResponse.of(n, actors.get(n.getActorId()), briefOf(n, posts)))
						.toList(),
				page.nextCursor(), page.hasNext());
	}

	@Transactional(readOnly = true)
	public long unreadCount(Long userId) {
		return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
	}

	/**
	 * 하나를 읽음으로 표시한다.
	 *
	 * <p>이미 읽은 것을 다시 눌러도 성공이다 — 요청의 목적("읽은 상태")이 이미 이뤄져 있다.
	 * 남의 알림이면 404다. 403이 아닌 이유는 알림이 공개물이 아니라서다. 403은 "그런 알림이
	 * 있긴 하다"를 알려준다.
	 */
	@Transactional
	public void markRead(Long userId, Long notificationId) {
		if (notificationRepository.markRead(notificationId, userId, LocalDateTime.now(clock)) == 0
				&& !notificationRepository.existsByIdAndRecipientId(notificationId, userId)) {
			throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
		}
	}

	@Transactional
	public void markAllRead(Long userId) {
		notificationRepository.markAllRead(userId, LocalDateTime.now(clock));
	}

	private NotificationResponse.PostBrief briefOf(Notification notification, Map<Long, Post> posts) {
		if (notification.getTargetType() != NotificationTarget.POST) {
			return null;
		}
		Post post = posts.get(notification.getTargetId());
		return post == null ? null : new NotificationResponse.PostBrief(post.getId(), post.getContent());
	}
}
