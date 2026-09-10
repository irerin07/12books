package com.irene.twelvebooks.follow;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {

	/**
	 * 피드가 매 요청 부르는 조회. 팔로잉 ID 목록을 그대로 {@code author_id IN (...)}에 넣는다.
	 *
	 * <p>{@code uk(follower_id, followee_id)}가 이것을 통째로 커버한다 — 좁히는 컬럼과 읽는 컬럼이
	 * 둘 다 인덱스 안에 있어 테이블을 보지 않는다.
	 *
	 * <p>Redis 캐시 후보이지만 넣지 않는다 — 팔로잉 수천 명 이전에는 문제가 되지 않고,
	 * 실측 근거 없이 캐시를 넣으면 무효화 규칙만 늘어난다.
	 */
	@Query("select f.followeeId from Follow f where f.followerId = :followerId")
	List<Long> findFolloweeIds(@Param("followerId") Long followerId);

	/**
	 * 나를 팔로우하는 사람 한 페이지. <b>최근에 팔로우한 사람이 먼저</b> 나온다.
	 *
	 * <p>정렬과 커서가 관계의 id라 다른 목록과 같은 모양이고, {@code (followee_id, id desc)}
	 * 인덱스가 그 순서를 그대로 준다.
	 */
	@Query("""
			select f from Follow f
			where f.followeeId = :followeeId
			  and (:cursor is null or f.id < :cursor)
			order by f.id desc
			""")
	List<Follow> findFollowerPage(@Param("followeeId") Long followeeId,
			@Param("cursor") Long cursor, Pageable pageable);

	/** 내가 팔로우하는 사람 한 페이지. 역시 최근에 팔로우한 순서다. */
	@Query("""
			select f from Follow f
			where f.followerId = :followerId
			  and (:cursor is null or f.id < :cursor)
			order by f.id desc
			""")
	List<Follow> findFolloweePage(@Param("followerId") Long followerId,
			@Param("cursor") Long cursor, Pageable pageable);

	/**
	 * 팔로워·팔로잉 수. 반정규화 카운터를 두지 않고 세는 것으로 시작한다 —
	 * 카운터는 갱신 유실과 불일치를 안고 오므로 실제로 느려진 뒤에 도입한다.
	 */
	long countByFolloweeId(Long followeeId);

	long countByFollowerId(Long followerId);

	void deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);
}
