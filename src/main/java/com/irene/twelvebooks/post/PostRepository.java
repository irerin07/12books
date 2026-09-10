package com.irene.twelvebooks.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
	 * 탐색 피드 한 페이지. 팔로우 관계와 무관한 전체 최신순이라 조건이 커서뿐이다.
	 *
	 * <p>팔로우한 사람이 없어도 피드가 성립해야 신규 사용자가 빈 화면을 보지 않는다.
	 */
	@Query("""
			select p from Post p
			where (:cursor is null or p.id < :cursor)
			order by p.id desc
			""")
	List<Post> findExplorePage(@Param("cursor") Long cursor, Pageable pageable);
}
