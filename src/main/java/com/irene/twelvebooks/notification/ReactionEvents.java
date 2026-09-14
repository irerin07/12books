package com.irene.twelvebooks.notification;

/**
 * 알림을 만들 거리가 생겼다는 신호.
 *
 * <p>좋아요·댓글·팔로우 서비스가 알림 코드를 직접 부르지 않고 이것을 띄운다. 부르면
 * 세 서비스가 알림을 알게 되고, 알림 저장 실패가 그쪽 트랜잭션을 되돌린다. 신호만 띄우면
 * 받는 쪽이 <b>커밋 이후에</b> 따로 처리한다 — {@link NotificationListener} 참고.
 *
 * <p>받는 사람은 여기서 정하지 않는다. "글쓴이가 누구인가"는 알림의 관심사가 아니라 그
 * 도메인의 사실이라, 신호를 띄우는 쪽이 이미 알고 있는 값을 담는다.
 */
public final class ReactionEvents {

	private ReactionEvents() {
	}

	/** @param authorId 글쓴이 — 알림을 받는 사람 */
	public record PostLiked(Long postId, Long authorId, Long actorId) {
	}

	public record PostCommented(Long postId, Long authorId, Long actorId) {
	}

	/** @param followeeId 팔로우당한 사람 — 알림을 받는 사람 */
	public record Followed(Long followeeId, Long followerId) {
	}
}
