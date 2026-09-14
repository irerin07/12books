package com.irene.twelvebooks.notification;

/**
 * 알림이 가리키는 대상의 종류.
 *
 * <p>팔로우에는 딸린 글이 없지만 {@code USER}로 채운다. 비워 두면 유니크 제약이 발동하지
 * 않아서다 — 유니크 인덱스는 NULL을 서로 다른 값으로 보므로, 같은 사람이 팔로우를 껐다
 * 켤 때마다 알림이 쌓인다.
 */
public enum NotificationTarget {

	POST,
	USER
}
