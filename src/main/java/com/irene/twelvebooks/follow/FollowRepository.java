package com.irene.twelvebooks.follow;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	/**
	 * 내가 이 사람을 팔로우 중인지. 프로필이 팔로우 버튼을 <b>처음 그릴 때</b> 필요하다.
	 *
	 * <p>{@code uk(follower_id, followee_id)}를 그대로 타므로 관계가 몇 개든 인덱스 조회 한 번이다.
	 * 목록을 받아 와 뒤지는 것과는 비용이 다르다 — 팔로잉이 500명이어도 여기서는 한 번이다.
	 */
	boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

	/**
	 * 주어진 사람들 중 <b>보는 사람이 팔로우 중인</b> 사람들의 id.
	 *
	 * <p>목록 한 쪽을 그리려면 스무 명 각각에 대해 관계를 알아야 하는데, 한 명씩 물으면 쿼리가
	 * 목록 크기만큼 늘어난다. 페이지의 id를 모아 한 번에 묻고 Set으로 만들어 맞춘다 —
	 * 목록이 20명이든 50명이든 이 조회는 한 번이다.
	 */
	@Query("""
			select f.followeeId from Follow f
			where f.followerId = :viewerId and f.followeeId in :candidateIds
			""")
	List<Long> findFollowedAmong(@Param("viewerId") Long viewerId,
			@Param("candidateIds") List<Long> candidateIds);

	/**
	 * 언팔로우. <b>한 문장</b>으로 지운다.
	 *
	 * <p>이름에서 파생된 {@code deleteBy...}는 조회 후 엔티티 삭제로 실행된다. 그러면 두 요청이
	 * 같은 행을 함께 읽은 뒤 차례로 지우게 되고, 뒤엣것의 delete가 0행을 만나 Hibernate가
	 * "예상 1행, 실제 0행"으로 예외를 던진다 — 사용자에게는 500이다. "두 번 눌러도 성공"이라는
	 * 정책이 동시 요청에서만 조용히 깨진다.
	 *
	 * <p>한 문장으로 지우면 0행은 그냥 0행이다. 사전 조회도 사라진다.
	 *
	 * @return 지운 행 수. 0이면 애초에 팔로우한 적이 없다는 뜻이고, 그것도 성공이다.
	 */
	@Modifying
	@Query("delete from Follow f where f.followerId = :followerId and f.followeeId = :followeeId")
	int deleteRelation(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);
}
