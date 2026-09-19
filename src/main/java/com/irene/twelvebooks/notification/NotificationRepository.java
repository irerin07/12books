package com.irene.twelvebooks.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/**
	 * 내 알림 한 페이지. {@code (recipient_id, id desc)} 인덱스를 그대로 탄다.
	 *
	 * <p>읽은 알림도 함께 준다. 읽었다고 사라지면 "방금 뭐였지"를 다시 볼 수 없다.
	 */
	/**
	 * 차단한 사람이 만든 알림은 빼고 센다.
	 *
	 * <p><b>목록과 안 읽은 수가 같은 기준이어야 한다.</b> 목록에서만 빼면 배지에 1이 떠 있는데
	 * 열면 비어 있고, 사용자는 읽을 수 없는 알림을 영영 들고 다닌다.
	 *
	 * <p>행은 지우지 않는다. 차단을 풀면 돌아온다 — 알림은 "이런 일이 있었다"는 기록이고
	 * 차단이 그 일을 없던 것으로 만들지는 않는다.
	 *
	 * <p>받는 사람이 곧 보는 사람이라 방향을 둘 다 볼 필요가 있다 — 내가 차단했든 상대가
	 * 나를 차단했든 그 사람은 보이지 않는다.
	 */
	String NOT_BLOCKED = """
			  and not exists (select 1 from Block bl
				where (bl.blockerId = n.recipientId and bl.blockedId = n.actorId)
				   or (bl.blockerId = n.actorId and bl.blockedId = n.recipientId))
			""";

	@Query("""
			select n from Notification n
			where n.recipientId = :recipientId
			""" + NOT_BLOCKED + """
			  and (:cursor is null or n.id < :cursor)
			order by n.id desc
			""")
	List<Notification> findPage(@Param("recipientId") Long recipientId,
			@Param("cursor") Long cursor, Pageable pageable);

	/**
	 * 안 읽은 개수.
	 *
	 * <p>반정규화 카운터를 두지 않고 센다. {@code users}에 카운터를 더하면 알림 insert가 외래 키
	 * 때문에 그 행에 잡는 공유 잠금과 카운터 UPDATE의 배타 잠금이 물려 교착이 난다 —
	 * Phase 6에서 세 번 겪은 그 함정이다. 실제로 느려진 뒤에 도입한다.
	 */
	@Query("""
			select count(n) from Notification n
			where n.recipientId = :recipientId and n.readAt is null
			""" + NOT_BLOCKED)
	long countUnread(@Param("recipientId") Long recipientId);

	/**
	 * 하나를 읽음으로 표시한다. <b>받는 사람 조건이 쿼리 안에 있다</b> — 먼저 조회해서 확인하면
	 * 남의 알림인지 보려고 그 내용을 읽어 오게 된다.
	 *
	 * @return 바뀐 행 수. 0이면 <b>이미 읽었거나</b> 없거나 남의 것이다. 셋을 여기서 가르지
	 *         않고 호출부가 {@link #existsByIdAndRecipientId}로 한 번 더 묻는다 — 흔한 경로에는
	 *         쿼리를 더하지 않고, 0행이 나온 드문 경우에만 확인한다.
	 */
	@Modifying
	@Query("""
			update Notification n set n.readAt = :now
			where n.id = :id and n.recipientId = :recipientId and n.readAt is null
			""")
	int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId,
			@Param("now") LocalDateTime now);

	/**
	 * 내 알림인지. 읽음 처리가 0행일 때만 부른다 — "이미 읽은 내 알림"과 "남의 알림"을
	 * 가르기 위해서다. 앞은 성공으로, 뒤는 404로 끝난다.
	 *
	 * <p>남의 알림에 403이 아니라 404를 주는 것은 감상평과 다르다. 알림은 공개물이 아니라
	 * 존재 자체가 사적인 정보다 — 403은 "그런 알림이 있긴 하다"를 알려준다.
	 */
	boolean existsByIdAndRecipientId(Long id, Long recipientId);

	/** 안 읽은 것을 모두 읽음으로. 이미 읽은 것의 시각을 덮지 않는다. */
	@Modifying
	@Query("""
			update Notification n set n.readAt = :now
			where n.recipientId = :recipientId and n.readAt is null
			""")
	int markAllRead(@Param("recipientId") Long recipientId, @Param("now") LocalDateTime now);
}
