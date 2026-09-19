package com.irene.twelvebooks.follow;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// allow-no-block-filter: 관계 목록이다. 내가 차단해도 상대가 나를 언팔한 것은 아니므로
// 관계는 그대로 보인다. 가리는 것은 그 사람의 내용(글·댓글·프로필)이지 관계가 아니다
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
	 *
	 * <p><b>탈퇴한 사람은 쿼리에서 뺀다.</b> 받아 온 뒤에 거르면 스무 개를 청구했는데 열여덟
	 * 개가 오는 페이지가 되고, 같은 handle로 새 계정이 가입하면 목록에는 옛 사람이 보이는데
	 * 링크는 새 사람으로 가서 엉뚱한 사람을 팔로우하게 된다.
	 *
	 * <p><b>차단은 여기서 거르지 않는다.</b> 내가 누군가를 차단해도 <b>그 사람이 나를 언팔한
	 * 것은 아니다</b> — 관계는 그대로 있고, 목록은 관계를 보여 주는 자리다. 지우면 "차단했더니
	 * 내 팔로워가 줄었다"가 되는데, 차단은 내 의사이지 상대의 관계를 끊을 근거가 아니다.
	 *
	 * <p>그래서 팔로워 수도 <b>모두에게 같은 값</b>이다. 보는 사람마다 다른 수를 주면
	 * "A의 팔로워 수"가 A의 속성이 아니라 (A, 보는 사람)의 함수가 된다.
	 *
	 * <p>차단한 사람의 <b>글·댓글·프로필</b>은 여전히 보이지 않는다 — 목록에서 이름을 눌러도
	 * {@code BlockGuard}가 404로 막는다. 가리는 것은 그 사람의 <b>내용</b>이지 관계가 아니다.
	 */
	@Query("""
			select f from Follow f
			where f.followeeId = :followeeId
			  and exists (select 1 from User u where u.id = f.followerId and u.deletedAt is null)
			  and (:cursor is null or f.id < :cursor)
			order by f.id desc
			""")
	List<Follow> findFollowerPage(@Param("followeeId") Long followeeId,
			@Param("cursor") Long cursor, Pageable pageable);

	/** 내가 팔로우하는 사람 한 페이지. 역시 최근에 팔로우한 순서이고, 탈퇴한 사람은 빠진다. */
	@Query("""
			select f from Follow f
			where f.followerId = :followerId
			  and exists (select 1 from User u where u.id = f.followeeId and u.deletedAt is null)
			  and (:cursor is null or f.id < :cursor)
			order by f.id desc
			""")
	List<Follow> findFolloweePage(@Param("followerId") Long followerId,
			@Param("cursor") Long cursor, Pageable pageable);

	/**
	 * 팔로워·팔로잉 수. 반정규화 카운터를 두지 않고 세는 것으로 시작한다 —
	 * 카운터는 갱신 유실과 불일치를 안고 오므로 실제로 느려진 뒤에 도입한다.
	 *
	 * <p>목록과 <b>같은 기준</b>으로 센다. 목록에서만 빼면 "팔로워 1명"을 눌렀는데 빈 화면이 된다.
	 */
	@Query("""
			select count(f) from Follow f
			where f.followeeId = :followeeId
			  and exists (select 1 from User u where u.id = f.followerId and u.deletedAt is null)
			""")
	long countFollowers(@Param("followeeId") Long followeeId);

	@Query("""
			select count(f) from Follow f
			where f.followerId = :followerId
			  and exists (select 1 from User u where u.id = f.followeeId and u.deletedAt is null)
			""")
	long countFollowings(@Param("followerId") Long followerId);

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
