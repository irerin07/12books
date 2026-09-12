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
			  and (:cursor is null or c.id < :cursor)
			order by c.id desc
			""")
	List<Comment> findPostPage(@Param("postId") Long postId, @Param("cursor") Long cursor,
			Pageable pageable);

	/** 살아 있는 댓글 하나. 지운 댓글은 없는 것과 같이 답한다. */
	@Query("select c from Comment c where c.id = :commentId and c.deletedAt is null")
	Optional<Comment> findLive(@Param("commentId") Long commentId);

	/**
	 * 댓글을 지운다 — 행이 아니라 플래그를 세운다. <b>한 문장</b>으로 한다.
	 *
	 * <p>조회한 엔티티를 고쳐 지우면, 댓글 작성자와 글 작성자가 동시에 눌렀을 때 둘 다 같은
	 * 행을 읽고 차례로 쓰게 되어 카운터가 두 번 내려간다. 한 문장에 {@code deletedAt is null}을
	 * 달면 먼저 온 쪽만 1행을 받는다.
	 *
	 * @return 지운 행 수. <b>1일 때만</b> 카운터를 내린다. 0이면 그사이 다른 요청이 지웠다는
	 *         뜻이고, 그쪽이 이미 카운터를 내렸다.
	 */
	@Modifying
	@Query("update Comment c set c.deletedAt = :now where c.id = :commentId and c.deletedAt is null")
	int softDelete(@Param("commentId") Long commentId, @Param("now") LocalDateTime now);
}
