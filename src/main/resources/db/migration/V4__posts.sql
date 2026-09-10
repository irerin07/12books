-- 감상평. 제품의 핵심 콘텐츠이고, 여기서부터 기록이 공개된 글이 된다.
create table posts
(
    id            bigint         not null auto_increment,
    author_id     bigint         not null,
    -- book_id는 reading을 타고 가면 알 수 있지만 여기 비정규화해 둔다. 책별 감상평 조회가
    -- 가장 잦은 목록인데, 조인을 거치면 (book_id, id desc) 하나로 끝날 일이 매번 두 테이블이 된다.
    book_id       bigint         not null,
    -- 서재 기록이 지워져도 글은 남는다. 서재에서 뺀 것은 "안 읽기로 했다"는 뜻이지
    -- "쓴 글을 없애 달라"는 뜻이 아니다. 그래서 nullable + set null이다.
    reading_id    bigint,
    content       varchar(1000)  not null,
    from_page     int,
    to_page       int,
    spoiler       boolean        not null default false,
    -- 반정규화 카운터. Phase 6이 원자적 UPDATE로 올린다. 여기서는 0으로만 시작한다.
    like_count    int            not null default 0,
    comment_count int            not null default 0,
    created_at    datetime(6)    not null,
    updated_at    datetime(6)    not null,
    primary key (id),
    constraint fk_posts_author foreign key (author_id) references users (id),
    -- 책은 여러 사람의 글이 매달린 독립 생명주기라 readings와 같은 이유로 RESTRICT다.
    constraint fk_posts_book foreign key (book_id) references books (id),
    constraint fk_posts_reading foreign key (reading_id) references readings (id) on delete set null,
    -- 읽은 구간은 1쪽부터다. 0쪽은 없다.
    constraint ck_posts_from_page check (from_page is null or from_page >= 1),
    constraint ck_posts_to_page check (to_page is null or to_page >= 1),
    -- 순서 규칙은 DTO도 지키지만, 엔티티를 거치지 않는 쓰기가 생겨도 스키마가 마지막으로 막는다.
    constraint ck_posts_page_range check (from_page is null or to_page is null or from_page <= to_page),
    constraint ck_posts_counters check (like_count >= 0 and comment_count >= 0)
) engine = InnoDB
  default charset = utf8mb4;

-- 내 글 목록과 팔로잉 타임라인(Phase 5)이 타는 경로. 정렬이 id desc라 인덱스도 그 방향이다.
create index idx_posts_author_id_desc on posts (author_id, id desc);

-- 책별 감상평. "같은 책을 읽는 사람들이 만난다"(spec.md §1.4)가 이 인덱스 위에서 돈다.
create index idx_posts_book_id_desc on posts (book_id, id desc);
