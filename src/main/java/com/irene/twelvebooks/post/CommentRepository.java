package com.irene.twelvebooks.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	/**
	 * 댓글을 <b>한 문장</b>으로 지운다.
	 *
	 * <p>조회한 엔티티를 지우면, 댓글 작성자와 글 작성자가 동시에 눌렀을 때 둘 다 같은 행을
	 * 읽고 차례로 지우게 된다. 뒤엣것의 DELETE가 0행을 만나 Hibernate가 "예상 1행, 실제 0행"으로
	 * 예외를 던지고 사용자에게는 500이 된다 — 언팔로우에서 이미 한 번 겪은 모양이다.
	 * 한 문장으로 지우면 0행은 그냥 0행이다.
	 *
	 * @return 지운 행 수. <b>1일 때만</b> 카운터를 내린다. 0이면 그사이 다른 요청이 지웠다는
	 *         뜻이고, 그쪽이 이미 카운터를 내렸다.
	 */
	@Modifying
	@Query("delete from Comment c where c.id = :commentId")
	int deleteComment(@Param("commentId") Long commentId);
}
