package com.irene.twelvebooks.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

	/**
	 * 그 책의 감상평 한 페이지. {@code (book_id, id desc)} 인덱스를 그대로 탄다.
	 *
	 * <p>커서가 곧 id다 — PK가 auto-increment라 {@code id desc}가 최신순이고, 같은 시각에
	 * 여러 글이 들어와도 순서가 흔들리지 않는다. {@code size + 1}건을 가져와 다음 페이지
	 * 존재를 판정한다.
	 */
	@Query("""
			select p from Post p
			where p.bookId = :bookId
			  and (:cursor is null or p.id < :cursor)
			order by p.id desc
			""")
	List<Post> findBookPage(@Param("bookId") Long bookId, @Param("cursor") Long cursor, Pageable pageable);

	/**
	 * 한 사람이 쓴 감상평 한 페이지. {@code (author_id, id desc)} 인덱스를 그대로 탄다.
	 *
	 * <p>프로필의 글 목록이자 "내 글만 보기"다. 둘은 같은 질문이라 경로를 나누지 않는다 —
	 * 내 handle로 부르면 내 글이다.
	 */
	@Query("""
			select p from Post p
			where p.authorId = :authorId
			  and (:cursor is null or p.id < :cursor)
			order by p.id desc
			""")
	List<Post> findAuthorPage(@Param("authorId") Long authorId, @Param("cursor") Long cursor,
			Pageable pageable);

	/**
	 * 홈 한 페이지. <b>내 글만 빼고</b> 전체 최신순이다.
	 *
	 * <p>팔로우한 사람의 글과 아닌 글이 한 목록에 섞여 나온다 — 인스타·트위터의 홈과 같다.
	 * 둘을 따로 조회해 클라이언트가 이어 붙이면 팔로우한 사람의 글이 양쪽에 다 나와 중복되고,
	 * 커서도 둘을 따로 굴려야 한다. 한 쿼리·한 커서면 그 문제가 구조적으로 없다.
	 *
	 * <p>어느 글이 팔로잉인지는 서비스가 표시해 준다.
	 */
	@Query("""
			select p from Post p
			where p.authorId <> :viewerId
			  and (:cursor is null or p.id < :cursor)
			order by p.id desc
			""")
	List<Post> findHomePage(@Param("viewerId") Long viewerId, @Param("cursor") Long cursor,
			Pageable pageable);

	/**
	 * 팔로잉 타임라인 한 페이지. 팔로잉 ID 목록을 그대로 {@code IN}에 넣는
	 * <b>fan-out on read</b>이고, {@code (author_id, id desc)} 인덱스가 이것을 커버한다.
	 *
	 * <p>쓰기 시점에 팔로워마다 복사해 두는 팬아웃 쓰기나 Redis 타임라인은 넣지 않는다 —
	 * 실제 지연이 관측되기 전에 도입하면 무효화 규칙만 늘어난다.
	 *
	 * <p>{@code authorIds}에는 <b>본인이 들어가지 않는다.</b> 홈에 내 글과 남의 글이 섞이면
	 * 무엇을 보는 화면인지 흐려진다. 내 글은 {@code findAuthorPage}로 따로 본다.
	 */
	@Query("""
			select p from Post p
			where p.authorId in :authorIds
			  and (:cursor is null or p.id < :cursor)
			order by p.id desc
			""")
	List<Post> findTimelinePage(@Param("authorIds") List<Long> authorIds,
			@Param("cursor") Long cursor, Pageable pageable);
}
