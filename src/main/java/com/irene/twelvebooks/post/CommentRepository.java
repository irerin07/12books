package com.irene.twelvebooks.post;

import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

	/**
	 * 운영 처리 전에 대상 행을 <b>먼저</b> 배타로 잡는다.
	 *
	 * <p>복구 판단이 {@code reports}를 읽으므로 이 경로는 두 테이블을 만진다. 순서를
	 * {@code comments → reports}로 고정하지 않으면, 동시 기각 둘이 각자 상대의 신고 행에 공유
	 * 잠금을 쥔 채 자기 신고 행에 배타 잠금을 올리려 해 <b>교착</b>에 빠진다.
	 *
	 * <p>{@code 이 조회}는 숨김·삭제 여부를 가리지 않는다 — 되돌릴 대상은 정의상 숨겨진 것이고,
	 * 조건을 달면 잠글 행을 못 찾아 순서 고정이 깨진다. id만 읽는 것도 의도다. 엔티티를
	 * 읽으면 영속성 컨텍스트에 올라가 뒤이은 UPDATE와 상태가 엇갈린다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c.id from Comment c where c.id = :commentId")
	Optional<Long> lockForModeration(@Param("commentId") Long commentId);

	/**
	 * 이 신고를 빼고 인정된 신고가 없을 때만 다시 올린다.
	 *
	 * <p>판단이 UPDATE 안에 있는 이유는 {@code PostRepository}의 같은 이름 메서드에 적었다 —
	 * 밖에서 먼저 물으면 동시 기각 둘이 서로를 보고 둘 다 포기한다.
	 *
	 * @return 바뀐 행 수. 0이면 아직 인정된 신고가 남아 있거나, 이미 올라왔거나, 지워졌다.
	 */
	@Modifying
	@Query("""
			update Comment c set c.hiddenAt = null
			where c.id = :commentId and c.hiddenAt is not null and c.deletedAt is null
			  and not exists (
				select 1 from Report r
				where r.targetType = com.irene.twelvebooks.report.ReportTarget.COMMENT
				  and r.targetId = :commentId
				  and r.status = com.irene.twelvebooks.report.ReportStatus.ACTIONED
				  and r.id <> :exceptReportId)
			""")
	int unhideIfLastActionedReport(@Param("commentId") Long commentId,
			@Param("exceptReportId") Long exceptReportId);

	/** 운영자 목록에 곁들일 댓글. 지운 것도 숨긴 것도 나온다 — 판단하려면 봐야 한다. */
	@Query("""
			select new com.irene.twelvebooks.report.ContentView(c.id, c.content)
			from Comment c where c.id in :commentIds
			""")
	List<com.irene.twelvebooks.report.ContentView> findAnyCommentViews(
			@Param("commentIds") List<Long> commentIds);

}
