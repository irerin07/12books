package com.irene.twelvebooks.notification;

/**
 * 알림이 생기는 일.
 *
 * <p>이름을 {@code LIKE}가 아니라 {@code POST_LIKED}로 둔 것은, 나중에 댓글 좋아요 같은 것이
 * 생겼을 때 무엇에 대한 반응인지 이름만으로 구분되게 하기 위해서다. 화면은 이 값으로
 * 문구를 고르므로 한 번 정하면 바꾸기 어렵다.
 */
public enum NotificationType {

	POST_LIKED,
	POST_COMMENTED,
	FOLLOWED
}
