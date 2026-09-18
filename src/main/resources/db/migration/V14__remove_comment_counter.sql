-- 댓글 수는 공개 댓글을 조회할 때 계산한다. 원본 댓글은 삭제하지 않는다.
alter table posts
    drop check ck_posts_counters,
    drop column comment_count,
    add constraint ck_posts_like_count check (like_count >= 0);
