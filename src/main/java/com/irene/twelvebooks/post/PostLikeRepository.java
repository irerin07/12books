package com.irene.twelvebooks.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

	/**
	 * 주어진 글들 중 <b>보는 사람이 좋아요를 누른</b> 글의 id.
	 *
	 * <p>목록 한 쪽을 그리려면 스무 글 각각에 대해 이 관계를 알아야 하는데, 글마다 물으면
	 * 쿼리가 목록 크기만큼 늘어난다. 페이지의 id를 모아 한 번에 묻고 Set으로 맞춘다 —
	 * 한 페이지가 20건이든 50건이든 이 조회는 한 번이다. 팔로워 목록의
	 * {@code findFollowedAmong}과 같은 모양이다.
	 *
	 * <p>{@code uk(post_id, user_id)}가 이것을 통째로 커버한다.
	 */
	@Query("""
			select l.postId from PostLike l
			where l.userId = :viewerId and l.postId in :postIds
			""")
	List<Long> findLikedAmong(@Param("viewerId") Long viewerId, @Param("postIds") List<Long> postIds);

	/**
	 * 좋아요 취소. <b>한 문장</b>으로 지운다.
	 *
	 * <p>이름에서 파생된 {@code deleteBy...}는 조회 후 엔티티 삭제로 실행된다. 그러면 두 요청이
	 * 같은 행을 함께 읽은 뒤 차례로 지우게 되고, 뒤엣것의 delete가 0행을 만나 Hibernate가
	 * 예외를 던진다 — "두 번 눌러도 성공"이 동시 요청에서만 조용히 깨진다(follows와 같은 이유).
	 *
	 * @return 지운 행 수. <b>이 값이 1일 때만</b> 카운터를 내린다. 0은 애초에 누른 적이 없다는
	 *         뜻이고, 그때도 카운터를 내리면 좋아요를 두 번 취소해 음수를 만들 수 있다.
	 */
	@Modifying
	@Query("delete from PostLike l where l.postId = :postId and l.userId = :userId")
	int deleteLike(@Param("postId") Long postId, @Param("userId") Long userId);
}
