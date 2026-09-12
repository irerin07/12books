package com.irene.twelvebooks.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
	 * 홈 한 페이지. <b>내 글과 내가 팔로우하는 사람의 글을 뺀</b> 최신순이다.
	 *
	 * <p>팔로잉을 빼기 때문에 홈과 팔로잉 목록이 <b>서로 겹치지 않는다</b> — 화면이 둘을 이어
	 * 붙여도 같은 글이 두 번 나오지 않고, 섞는 비율은 화면이 정한다.
	 *
	 * <p>{@code excludedIds}에는 항상 본인이 들어가므로 빈 컬렉션이 될 수 없다. 빈 목록을
	 * {@code in ()}으로 넘기면 DB마다 다르게 구는데, 그 경우가 아예 생기지 않는다.
	 */
	@Query("""
			select p from Post p
			where p.authorId not in :excludedIds
			  and (:cursor is null or p.id < :cursor)
			order by p.id desc
			""")
	List<Post> findHomePage(@Param("excludedIds") List<Long> excludedIds,
			@Param("cursor") Long cursor, Pageable pageable);

	/**
	 * 팔로잉 타임라인 한 페이지. 팔로잉 ID 목록을 그대로 {@code IN}에 넣는
	 * <b>fan-out on read</b>이고, {@code (author_id, id desc)} 인덱스가 이것을 커버한다.
	 *
	 * <p>쓰기 시점에 팔로워마다 복사해 두는 팬아웃 쓰기나 Redis 타임라인은 넣지 않는다 —
	 * 실제 지연이 관측되기 전에 도입하면 무효화 규칙만 늘어난다.
	 *
	 * <p>{@code authorIds}에 본인은 들어가지 않는다. 내 글은 {@code findAuthorPage}로 본다.
	 */
	@Query("""
			select p from Post p
			where p.authorId in :authorIds
			  and (:cursor is null or p.id < :cursor)
			order by p.id desc
			""")
	List<Post> findTimelinePage(@Param("authorIds") List<Long> authorIds,
			@Param("cursor") Long cursor, Pageable pageable);

	/**
	 * 좋아요 수를 <b>원자적으로</b> 올린다.
	 *
	 * <p>읽어서 더한 뒤 쓰면 동시에 들어온 두 요청이 같은 값을 읽고 같은 값을 써서 하나가
	 * 유실된다. 백 명이 동시에 누르면 카운터는 백이 아니다. DB가 행을 잠근 채로 더하게 하면
	 * 순서가 어떻든 결과가 같다(plan.md T5).
	 *
	 * <p>같은 이유로 엔티티에 {@code likeCount++}를 두지 않았다 — 그 메서드가 있으면 언젠가
	 * 누가 부른다.
	 *
	 * @return 바뀐 행 수. 0이면 그런 글이 없다는 뜻이라 존재 확인을 겸한다 — 앞에 따로
	 *         {@code exists} 조회를 두면 쿼리가 하나 늘 뿐 아니라 그 사이에 글이 지워질 수 있다.
	 */
	@Modifying
	@Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :postId")
	int increaseLikeCount(@Param("postId") Long postId);

	/**
	 * 좋아요 수를 원자적으로 내린다. 호출부가 <b>좋아요 행을 실제로 지웠을 때만</b> 부른다 —
	 * 누른 적 없는 사람의 취소에도 내리면 취소를 두 번 눌러 남의 좋아요를 지울 수 있다.
	 *
	 * <p>{@code likeCount > 0}은 그래도 남겨 둔다. 스키마의 CHECK에 걸려 500이 되기 전에
	 * 조건에서 막는 편이 낫다.
	 */
	@Modifying
	@Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :postId and p.likeCount > 0")
	void decreaseLikeCount(@Param("postId") Long postId);

	@Modifying
	@Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :postId")
	void increaseCommentCount(@Param("postId") Long postId);

	@Modifying
	@Query("update Post p set p.commentCount = p.commentCount - 1 where p.id = :postId and p.commentCount > 0")
	void decreaseCommentCount(@Param("postId") Long postId);
}
