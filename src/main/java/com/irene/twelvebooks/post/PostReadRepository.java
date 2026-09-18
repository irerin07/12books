package com.irene.twelvebooks.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/** 쓰기용 엔티티 조회와 분리한 게시글 응답 조회. 댓글 수를 저장하거나 보정하지 않는다. */
public interface PostReadRepository extends Repository<Post, Long> {
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
            """;
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
