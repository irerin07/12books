package com.irene.twelvebooks.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
			  and (:cursor is null or c.id < :cursor)
			order by c.id desc
			""")
	List<Comment> findPostPage(@Param("postId") Long postId, @Param("cursor") Long cursor,
			Pageable pageable);
}
