package com.irene.twelvebooks.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	/**
	 * 한 글에 달린 댓글 한 페이지. {@code (post_id, id desc)} 인덱스를 그대로 탄다.
	 *
	 * <p>정렬이 {@code id desc}인 것은 규약이기도 하고(plan.md T5: 모든 목록은 id 커서),
	 * 대댓글이 없어 순서가 대화의 계층을 뜻하지 않기 때문이다. 오래된 순으로 주려면 커서
	 * 방향이 이 프로젝트에서 이 목록만 반대가 된다 — 한 목록만 다른 규칙을 갖는 비용이
	 * 얻는 것보다 크다. 화면이 한 페이지를 뒤집어 그리는 것은 자유다.
	 */
	@Query("""
			select c from Comment c
			where c.postId = :postId
			  and c.deletedAt is null
			  and c.hiddenAt is null
			  and exists (select 1 from User u where u.id = c.authorId and u.deletedAt is null)
			  and (:cursor is null or c.id < :cursor)
			order by c.id desc
			""")
	List<Comment> findPostPage(@Param("postId") Long postId, @Param("cursor") Long cursor,
			Pageable pageable);

	/** 살아 있는 댓글 하나. 지운 댓글은 없는 것과 같이 답한다. */
	@Query("select c from Comment c where c.id = :commentId and c.deletedAt is null and c.hiddenAt is null and exists (select 1 from User u where u.id = c.authorId and u.deletedAt is null)")
	Optional<Comment> findLive(@Param("commentId") Long commentId);

	/** 이미 삭제됐다면 아무것도 바꾸지 않는다. */
	@Modifying
	@Query("update Comment c set c.deletedAt = :now where c.id = :commentId and c.deletedAt is null")
	int softDelete(@Param("commentId") Long commentId, @Param("now") LocalDateTime now);

	/** 작성자가 삭제하지 않은 댓글의 운영자 숨김 상태를 바꾼다. */
	@Modifying
	@Query("update Comment c set c.hiddenAt = :now where c.id = :commentId and c.hiddenAt is null and c.deletedAt is null")
	int hide(@Param("commentId") Long commentId, @Param("now") LocalDateTime now);

	@Modifying
	@Query("update Comment c set c.hiddenAt = null where c.id = :commentId and c.hiddenAt is not null and c.deletedAt is null")
	int unhide(@Param("commentId") Long commentId);

	/** 운영자 목록에 곁들일 댓글. 지운 것도 숨긴 것도 나온다 — 판단하려면 봐야 한다. */
	@Query("""
			select new com.irene.twelvebooks.report.ContentView(c.id, c.content)
			from Comment c where c.id in :commentIds
			""")
	List<com.irene.twelvebooks.report.ContentView> findAnyCommentViews(
			@Param("commentIds") List<Long> commentIds);

}
