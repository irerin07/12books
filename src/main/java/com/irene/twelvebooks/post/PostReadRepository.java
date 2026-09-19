package com.irene.twelvebooks.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/** 쓰기용 엔티티 조회와 분리한 게시글 응답 조회. 댓글 수를 저장하거나 보정하지 않는다. */
public interface PostReadRepository extends Repository<Post, Long> {
    /**
     * 차단은 <b>양방향</b>으로 가린다. 행은 누가 눌렀는지만 기록하지만(plan.md L2-2),
     * 차단한 쪽도 차단당한 쪽도 상대를 보지 못한다 — 한쪽만 막으면 차단이 "내 눈만 가리는 것"이
     * 되어 차단당한 사람이 글을 찾아와 댓글을 달 수 있다.
     *
     * <p><b>{@code SELECT}에 붙어 있는 것이 요점이다.</b> 이 인터페이스의 모든 조회가 그 상수를
     * 쓰므로 새 목록을 추가해도 조건이 따라온다. 목록마다 손으로 달면 언젠가 하나를 빠뜨리고,
     * 그건 아무도 모르는 종류의 실수다({@code BlockConventionTest}가 그것을 잡는다).
     *
     * <p>{@code :viewerId}가 {@code null}이면(비로그인) 비교가 전부 거짓이라 아무것도 걸리지
     * 않는다 — 차단은 사람 사이의 관계이므로 그게 맞다.
     */
    String BLOCKED = """
             and not exists (select 1 from Block bl
                where (bl.blockerId = :viewerId and bl.blockedId = p.authorId)
                   or (bl.blockerId = p.authorId and bl.blockedId = :viewerId))
            """;

    String SELECT = """
            select new com.irene.twelvebooks.post.PostReadRow(
                p.id, u.handle, u.displayName, u.avatarUrl,
                b.id, b.isbn13, b.title, b.authors, b.publisher, b.thumbnailUrl, b.publishedAt,
                p.readingId, p.content, p.fromPage, p.toPage, p.spoiler, p.likeCount,
                (select count(c) from Comment c where c.postId = p.id
                    and c.deletedAt is null and c.hiddenAt is null
                    and exists (select 1 from User cu where cu.id = c.authorId and cu.deletedAt is null)),
                exists (select 1 from PostLike l where l.postId = p.id and l.userId = :viewerId),
                p.createdAt)
            from Post p join User u on u.id = p.authorId join Book b on b.id = p.bookId
            where p.deletedAt is null and p.hiddenAt is null and u.deletedAt is null
            """ + BLOCKED;

    String PAGE = " and (:cursor is null or p.id < :cursor) order by p.id desc";

    @Query(SELECT + " and p.id = :postId")
    Optional<PostReadRow> findDetail(@Param("viewerId") Long viewerId, @Param("postId") Long postId);

    @Query(SELECT + " and p.bookId = :bookId" + PAGE)
    List<PostReadRow> findBookPage(@Param("viewerId") Long viewerId, @Param("bookId") Long bookId,
            @Param("cursor") Long cursor, Pageable pageable);

    @Query(SELECT + " and p.authorId = :authorId" + PAGE)
    List<PostReadRow> findAuthorPage(@Param("viewerId") Long viewerId, @Param("authorId") Long authorId,
            @Param("cursor") Long cursor, Pageable pageable);

    @Query(SELECT + " and p.authorId not in :excludedIds" + PAGE)
    List<PostReadRow> findHomePage(@Param("viewerId") Long viewerId, @Param("excludedIds") List<Long> excludedIds,
            @Param("cursor") Long cursor, Pageable pageable);

    @Query(SELECT + " and p.authorId in :authorIds" + PAGE)
    List<PostReadRow> findTimelinePage(@Param("viewerId") Long viewerId, @Param("authorIds") List<Long> authorIds,
            @Param("cursor") Long cursor, Pageable pageable);
}
