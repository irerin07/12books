package com.irene.twelvebooks.post;

import jakarta.persistence.LockModeType;
import com.irene.twelvebooks.notification.PostView;
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
			  and p.hiddenAt is null
			  and exists (select 1 from User u where u.id = p.authorId and u.deletedAt is null)
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
			  and p.hiddenAt is null
			  and exists (select 1 from User u where u.id = p.authorId and u.deletedAt is null)
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
			  and p.hiddenAt is null
			  and exists (select 1 from User u where u.id = p.authorId and u.deletedAt is null)
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
			  and p.hiddenAt is null
			  and exists (select 1 from User u where u.id = p.authorId and u.deletedAt is null)
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
	 *
	 * <p>작성자가 탈퇴했는지는 <b>여기서 보지 않는다.</b> {@code FOR UPDATE}는 서브쿼리가 읽은
	 * 행까지 잠그므로, 조건을 하나 더 달면 좋아요 취소가 {@code users} 행을 잠그게 된다 —
	 * 잠금 순서가 늘어나면 교착이 늘어난다. 보이지 않게 하는 일은 조회 쿼리가 맡는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Post p where p.id = :postId and p.deletedAt is null and p.hiddenAt is null")
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
	@Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :postId and p.deletedAt is null and p.hiddenAt is null")
	int increaseLikeCount(@Param("postId") Long postId);

	/**
	 * 좋아요 수를 원자적으로 내린다. 호출부가 <b>좋아요 행을 실제로 지웠을 때만</b> 부른다 —
	 * 누른 적 없는 사람의 취소에도 내리면 취소를 두 번 눌러 남의 좋아요를 지울 수 있다.
	 *
	 * <p>{@code likeCount > 0}은 그래도 남겨 둔다. 스키마의 CHECK에 걸려 500이 되기 전에
	 * 조건에서 막는 편이 낫다.
	 */
	@Modifying
	@Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :postId and p.likeCount > 0 and p.deletedAt is null and p.hiddenAt is null")
	void decreaseLikeCount(@Param("postId") Long postId);

	/**
	 * 댓글 수를 원자적으로 올린다. 좋아요와 같은 이유로 <b>댓글 행을 넣기 전에</b> 부른다 —
	 * {@code comments} insert가 외래 키 때문에 부모인 {@code posts} 행에 잡는 공유 잠금이,
	 * 뒤따르는 이 UPDATE의 배타 잠금과 물려 동시 요청을 교착에 빠뜨린다.
	 *
	 * @return 바뀐 행 수. 0이면 그런 글이 없다는 뜻이라 존재 확인을 겸한다.
	 */
	@Modifying
	@Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :postId and p.deletedAt is null and p.hiddenAt is null")
	int increaseCommentCount(@Param("postId") Long postId);

	/** 댓글 수를 원자적으로 내린다. 삭제도 부모를 먼저 잠근 뒤 자식 행을 지운다. */
	@Modifying
	@Query("update Post p set p.commentCount = p.commentCount - 1 where p.id = :postId and p.commentCount > 0 and p.deletedAt is null and p.hiddenAt is null")
	void decreaseCommentCount(@Param("postId") Long postId);

	/**
	 * 살아 있는 글 하나. 지운 글은 없는 것과 같이 답한다 — 지운 뒤에도 읽히면 삭제가 아니다.
	 *
	 * <p>{@code findById}를 그대로 쓰지 않는 이유이기도 하다. 상속받은 그 메서드는 지운 글도
	 * 돌려주므로, 사용자에게 보이는 경로에서는 반드시 이쪽을 쓴다.
	 */
	@Query("select p from Post p where p.id = :postId and p.deletedAt is null and p.hiddenAt is null and exists (select 1 from User u where u.id = p.authorId and u.deletedAt is null)")
	Optional<Post> findLive(@Param("postId") Long postId);

	/**
	 * 살아 있는 글 여럿. 목록에 글을 곁들일 때 쓴다.
	 *
	 * <p>상속받은 {@code findAllById}를 쓰면 <b>지운 글까지 딸려 온다.</b> 알림 목록에 그것을
	 * 그대로 실으면 지운 본문이 다시 보이고, 삭제가 삭제가 아니게 된다.
	 */
	@Query("select p from Post p where p.id in :postIds and p.deletedAt is null and p.hiddenAt is null and exists (select 1 from User u where u.id = p.authorId and u.deletedAt is null)")
	List<Post> findAllLive(@Param("postIds") List<Long> postIds);

	/**
	 * 알림 목록에 곁들일 글의 <b>id와 본문만</b>. 지운 글은 빠진다.
	 *
	 * <p>엔티티를 읽으면 쪽수·카운터·연결 같은 것이 전부 따라오는데 알림 한 줄에는 쓰이지
	 * 않는다.
	 */
	@Query("""
			select new com.irene.twelvebooks.notification.PostView(p.id, p.content)
			from Post p where p.id in :postIds and p.deletedAt is null
			  and p.hiddenAt is null
			""")
	List<PostView> findLivePostViews(@Param("postIds") List<Long> postIds);

	@Query("select count(p) > 0 from Post p where p.id = :postId and p.deletedAt is null and p.hiddenAt is null and exists (select 1 from User u where u.id = p.authorId and u.deletedAt is null)")
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

	/**
	 * 운영자가 글을 내리거나 다시 올린다.
	 *
	 * <p>{@code hiddenAt}의 현재 상태를 조건에 달아 <b>바뀔 때만 1행</b>이 된다. 두 운영자가
	 * 동시에 같은 신고를 처리해도 한쪽만 1을 받으므로, 뒤따르는 처리를 한 번만 하고 싶을 때
	 * 이 값을 보면 된다.
	 *
	 * <p>{@code findAnyPostViews}와 달리 지운 글도 대상에 넣는다 — 작성자가 지운 뒤에 신고가
	 * 처리될 수 있고, 그때 숨김 기록은 남아야 한다.
	 */
	@Modifying
	@Query("update Post p set p.hiddenAt = :now where p.id = :postId and p.hiddenAt is null")
	int hide(@Param("postId") Long postId, @Param("now") LocalDateTime now);

	@Modifying
	@Query("update Post p set p.hiddenAt = null where p.id = :postId and p.hiddenAt is not null")
	int unhide(@Param("postId") Long postId);

	/**
	 * 운영자 목록에 곁들일 글의 id와 본문. <b>지운 글도 숨긴 글도 나온다.</b>
	 *
	 * <p>사용자용 조회와 정반대인 것이 요점이다 — 내용을 못 보면 운영자가 무엇을 내릴지
	 * 판단할 수 없고, 이미 내린 것을 되돌릴지도 정할 수 없다.
	 */
	@Query("""
			select new com.irene.twelvebooks.report.ContentView(p.id, p.content)
			from Post p where p.id in :postIds
			""")
	List<com.irene.twelvebooks.report.ContentView> findAnyPostViews(@Param("postIds") List<Long> postIds);

	/**
	 * 글 행을 잠근다 — <b>숨겨졌든 지워졌든.</b>
	 *
	 * <p>운영자 처리가 이것을 쓴다. {@link #findByIdForUpdate}는 사용자에게 보이는 글만
	 * 잠그는데, 숨긴 글 아래의 댓글을 처리할 때는 그 글을 잠글 수 없어 <b>잠금 없이</b>
	 * 카운터를 만지게 된다. 그러면 작성자의 삭제와 부딪힌다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Post p where p.id = :postId")
	Optional<Post> findAnyByIdForUpdate(@Param("postId") Long postId);

	/**
	 * 운영자 처리가 쓰는 댓글 수 조정. <b>부모 글이 보이지 않아도 반영된다.</b>
	 *
	 * <p>사용자 경로의 조건(살아 있고 안 숨겨진 글)을 그대로 쓰면, 글을 숨긴 상태에서 그 아래
	 * 댓글을 내렸을 때 숫자가 줄지 않는다. 나중에 글을 되돌리면 보이는 댓글은 하나인데 숫자는
	 * 둘이다 — 되돌릴 수 있게 만든 장치가 되돌릴 때 어긋난다.
	 */
	@Modifying
	@Query("update Post p set p.commentCount = p.commentCount - 1 where p.id = :postId and p.commentCount > 0")
	void decreaseCommentCountByModerator(@Param("postId") Long postId);

	@Modifying
	@Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :postId")
	void increaseCommentCountByModerator(@Param("postId") Long postId);

	/**
	 * 한 사람이 탈퇴해 <b>보이지 않게 되는 댓글</b>만큼 글마다 댓글 수를 내린다.
	 *
	 * <p>조회 조건만 더하면 목록에서는 빠지는데 숫자는 그대로다 — "댓글 1개"를 눌렀는데
	 * 아무것도 없는 화면이 된다. 운영자 숨김에서 이미 한 번 겪은 자리다.
	 *
	 * <p>세는 대상은 <b>지금 세어져 있는 댓글</b>뿐이다(지워지지도 내려가지도 않은 것).
	 * 이미 빠진 것을 또 빼면 숫자가 실제보다 작아진다.
	 */
	@Modifying
	@Query("""
			update Post p set p.commentCount = p.commentCount -
				(select count(c) from Comment c
				 where c.postId = p.id and c.authorId = :authorId
				   and c.deletedAt is null and c.hiddenAt is null)
			where exists (select 1 from Comment c2
				 where c2.postId = p.id and c2.authorId = :authorId
				   and c2.deletedAt is null and c2.hiddenAt is null)
			""")
	int decreaseCommentCountsOf(@Param("authorId") Long authorId);
}
