package com.irene.twelvebooks.notification;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.common.support.PageSize;
import com.irene.twelvebooks.notification.dto.NotificationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 내 알림. 남의 알림을 볼 수 있는 경로는 없다 — 언제나 토큰의 주인 것만 준다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public CursorPage<NotificationResponse> list(@AuthUser Long userId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return notificationService.list(userId, cursor, PageSize.clamp(size));
	}

	/**
	 * 안 읽은 개수. 화면이 종 아이콘의 배지를 그리려고 자주 부른다.
	 *
	 * <p>숫자 하나지만 객체로 감싼다. 맨 숫자를 내려주면 나중에 "종류별 개수" 같은 것이
	 * 필요해졌을 때 응답 모양을 바꿔야 하고, 그때 클라이언트가 전부 깨진다.
	 */
	@GetMapping("/unread-count")
	public Map<String, Long> unreadCount(@AuthUser Long userId) {
		return Map.of("count", notificationService.unreadCount(userId));
	}

	@PatchMapping("/{id}/read")
	public ResponseEntity<Void> markRead(@AuthUser Long userId, @PathVariable Long id) {
		notificationService.markRead(userId, id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/read-all")
	public ResponseEntity<Void> markAllRead(@AuthUser Long userId) {
		notificationService.markAllRead(userId);
		return ResponseEntity.noContent().build();
	}
}
