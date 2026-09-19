package com.irene.twelvebooks.block;

import com.irene.twelvebooks.block.dto.BlockItemResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {

	// allow-no-block-filter: 차단 목록 자체다. 거르면 항상 빈 목록이 되어 풀 수도 없다
	/**
	 * 내가 차단한 사람 한 페이지. 관계의 id가 커서라 <b>최근에 차단한 사람이 먼저</b> 나온다.
	 *
	 * <p>탈퇴한 사람은 뺀다. 목록의 다른 곳과 같은 기준이다 — 받아 온 뒤에 거르면 스무 개를
	 * 청구했는데 열여덟 개가 오는 페이지가 된다.
	 *
	 * <p>표시할 값만 골라 담는다. 엔티티를 읽으면 쓰지 않을 컬럼까지 따라온다.
	 */
	@Query("""
			select new com.irene.twelvebooks.block.dto.BlockItemResponse(
				b.id, u.handle, u.displayName, u.avatarUrl)
			from Block b join User u on u.id = b.blockedId
			where b.blockerId = :blockerId and u.deletedAt is null
			  and (:cursor is null or b.id < :cursor)
			order by b.id desc
			""")
	List<BlockItemResponse> findBlockedPage(@Param("blockerId") Long blockerId,
			@Param("cursor") Long cursor, Pageable pageable);

	/**
	 * 두 사람 사이에 차단이 <b>어느 방향으로든</b> 있는가.
	 *
	 * <p>행은 한 방향(누가 눌렀나)이지만 보이지 않는 것은 양방향이라 둘 다 본다. 한쪽만 보면
	 * 차단이 "내 눈만 가리는 것"이 되어 차단당한 사람이 계속 따라다닐 수 있다.
	 *
	 * <p>{@code viewerId}가 {@code null}이면(비로그인) 비교가 전부 거짓이라 항상 false다 —
	 * 차단은 사람 사이의 관계이므로 그게 맞다.
	 */
	@Query("""
			select count(b) > 0 from Block b
			where (b.blockerId = :viewerId and b.blockedId = :otherId)
			   or (b.blockerId = :otherId and b.blockedId = :viewerId)
			""")
	boolean existsBetween(@Param("viewerId") Long viewerId, @Param("otherId") Long otherId);

	/**
	 * 차단을 푼다.
	 *
	 * <p>차단은 팔로우·좋아요와 같이 <b>실제로 지운다.</b> "행을 지우지 않는다" 규약의 대상이
	 * 아니라서다 — 차단은 켰다 껐다 하는 관계의 유무이고, 푼 뒤에 "언제 차단했었나"를 물을 일이
	 * 없다. 오히려 남겨 두면 유니크 제약이 다시 차단하는 것을 막는다(`CLAUDE.md`의 "그게 정말
	 * 삭제인가" — 되돌리는 것이 복구가 아니라 다음 행동인 자리다).
	 *
	 * @return 지운 행 수. 0이면 차단돼 있지 않았다는 뜻이고, 결과가 같으므로 오류가 아니다.
	 */
	@Modifying
	@Query("delete from Block b where b.blockerId = :blockerId and b.blockedId = :blockedId")
	int unblock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);
}
