package com.irene.twelvebooks.post;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
			  and p.deletedAt is null
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
			  and p.deletedAt is null
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
			  and p.deletedAt is null
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
			  and p.deletedAt is null
			  and (:cursor is null or p.id < :cursor)
			order by p.id desc
			""")
	List<Post> findTimelinePage(@Param("authorIds") List<Long> authorIds,
			@Param("cursor") Long cursor, Pageable pageable);

	/**
	 * 글 행을 <b>먼저 배타로 잡는다.</b> 반응(좋아요·댓글)은 모두 {@code posts}를 먼저 잠근 뒤
	 * 자식 행을 만진다 — 순서가 엇갈리면 한쪽이 자식 행 잠금을 쥔 채 부모를 기다리고 다른
	 * 쪽이 그 반대가 되어 교착에 빠진다.
	 *
	 * <p>좋아요 취소가 이 메서드를 쓴다. 취소는 <b>지운 행 수를 봐야</b> 카운터를 내릴지 정할 수
	 * 있어서 카운터 UPDATE를 먼저 둘 수 없고, 그래서 잠금만 따로 먼저 잡는다.
	 *
	 * <p>글이 없으면 빈 값이다. 잠글 것이 없으니 뒤이은 삭제도 0행이고, 취소는 어차피 멱등이다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Post p where p.id = :postId and p.deletedAt is null")
	Optional<Post> findByIdForUpdate(@Param("postId") Long postId);

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
	@Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :postId and p.deletedAt is null")
	int increaseLikeCount(@Param("postId") Long postId);

	/**
	 * 좋아요 수를 원자적으로 내린다. 호출부가 <b>좋아요 행을 실제로 지웠을 때만</b> 부른다 —
	 * 누른 적 없는 사람의 취소에도 내리면 취소를 두 번 눌러 남의 좋아요를 지울 수 있다.
	 *
	 * <p>{@code likeCount > 0}은 그래도 남겨 둔다. 스키마의 CHECK에 걸려 500이 되기 전에
	 * 조건에서 막는 편이 낫다.
	 */
	@Modifying
	@Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :postId and p.likeCount > 0 and p.deletedAt is null")
	void decreaseLikeCount(@Param("postId") Long postId);

	/**
	 * 댓글 수를 원자적으로 올린다. 좋아요와 같은 이유로 <b>댓글 행을 넣기 전에</b> 부른다 —
	 * {@code comments} insert가 외래 키 때문에 부모인 {@code posts} 행에 잡는 공유 잠금이,
	 * 뒤따르는 이 UPDATE의 배타 잠금과 물려 동시 요청을 교착에 빠뜨린다.
	 *
	 * @return 바뀐 행 수. 0이면 그런 글이 없다는 뜻이라 존재 확인을 겸한다.
	 */
	@Modifying
	@Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :postId and p.deletedAt is null")
	int increaseCommentCount(@Param("postId") Long postId);

	/** 댓글 수를 원자적으로 내린다. 삭제도 부모를 먼저 잠근 뒤 자식 행을 지운다. */
	@Modifying
	@Query("update Post p set p.commentCount = p.commentCount - 1 where p.id = :postId and p.commentCount > 0 and p.deletedAt is null")
	void decreaseCommentCount(@Param("postId") Long postId);

	/**
	 * 살아 있는 글 하나. 지운 글은 없는 것과 같이 답한다 — 지운 뒤에도 읽히면 삭제가 아니다.
	 *
	 * <p>{@code findById}를 그대로 쓰지 않는 이유이기도 하다. 상속받은 그 메서드는 지운 글도
	 * 돌려주므로, 사용자에게 보이는 경로에서는 반드시 이쪽을 쓴다.
	 */
	@Query("select p from Post p where p.id = :postId and p.deletedAt is null")
	Optional<Post> findLive(@Param("postId") Long postId);

	@Query("select count(p) > 0 from Post p where p.id = :postId and p.deletedAt is null")
	boolean existsLive(@Param("postId") Long postId);

	/**
	 * 글을 지운다 — 행이 아니라 플래그를 세운다.
	 *
	 * <p>{@code deletedAt is null} 조건이 붙어 있어 이미 지운 글에는 0행이다. 두 요청이 동시에
	 * 지워도 한쪽만 1을 받으므로, 뒤따르는 처리를 한 번만 하고 싶을 때 이 값을 보면 된다.
	 *
	 * @return 지운 행 수. 0이면 이미 지워졌거나 없는 글이다.
	 */
	@Modifying
	@Query("update Post p set p.deletedAt = :now where p.id = :postId and p.deletedAt is null")
	int softDelete(@Param("postId") Long postId, @Param("now") LocalDateTime now);
}
