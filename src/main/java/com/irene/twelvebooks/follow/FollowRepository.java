package com.irene.twelvebooks.follow;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

	/**
	 * 피드가 매 요청 부르는 조회. 팔로잉 ID 목록을 그대로 {@code author_id IN (...)}에 넣는다.
	 *
	 * <p>복합 PK가 {@code (follower_id, followee_id)}라 이 조회는 별도 인덱스 없이 PK만으로 끝난다.
	 *
	 * <p>Redis 캐시 후보이지만 넣지 않는다 — 팔로잉 수천 명 이전에는 문제가 되지 않고,
	 * 실측 근거 없이 캐시를 넣으면 무효화 규칙만 늘어난다.
	 */
	@Query("select f.followeeId from Follow f where f.followerId = :followerId")
	List<Long> findFolloweeIds(@Param("followerId") Long followerId);

	/**
	 * 나를 팔로우하는 사람 한 페이지. 정렬과 커서가 상대방 id인 것은 복합 PK라 정렬용 대리 키가
	 * 없기 때문이다. 최신순은 아니지만 <b>중복도 누락도 없는</b> 안정된 순서이고,
	 * {@code idx(followee_id)}가 그대로 이 순서를 준다.
	 */
	@Query("""
			select f.followerId from Follow f
			where f.followeeId = :followeeId
			  and (:cursor is null or f.followerId < :cursor)
			order by f.followerId desc
			""")
	List<Long> findFollowerIdPage(@Param("followeeId") Long followeeId,
			@Param("cursor") Long cursor, Pageable pageable);

	/** 내가 팔로우하는 사람 한 페이지. 이쪽은 PK 자체가 그 순서다. */
	@Query("""
			select f.followeeId from Follow f
			where f.followerId = :followerId
			  and (:cursor is null or f.followeeId < :cursor)
			order by f.followeeId desc
			""")
	List<Long> findFolloweeIdPage(@Param("followerId") Long followerId,
			@Param("cursor") Long cursor, Pageable pageable);

	/**
	 * 팔로워·팔로잉 수. 반정규화 카운터를 두지 않고 세는 것으로 시작한다 —
	 * 카운터는 갱신 유실과 불일치를 안고 오므로 실제로 느려진 뒤에 도입한다.
	 */
	long countByFolloweeId(Long followeeId);

	long countByFollowerId(Long followerId);

	void deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);
}
