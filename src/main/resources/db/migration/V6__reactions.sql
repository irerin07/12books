-- 반응. 기록이 혼잣말에서 대화가 되는 지점이다(spec.md §1.2).

create table post_likes
(
    -- 관계는 (어느 글, 누가)로 이미 유일하지만 대리 키를 둔다. 복합 PK를 쓰면 식별자를 직접
    -- 넣게 되어 JPA save()가 insert 대신 merge로 나가고, 그러면 아래 유니크 제약이 발동하지
    -- 못해 중복 좋아요가 409 대신 조용히 성공한다(plan.md T5, follows와 같은 이유).
    id         bigint      not null auto_increment,
    post_id    bigint      not null,
    user_id    bigint      not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    -- 중복 좋아요를 막는 1차 방어선. 응용이 먼저 조회해서 막으면 동시에 들어온 두 요청이
    -- 함께 통과한다.
    --
    -- 동시에 목록이 매 페이지 하는 조회(likedByMe)를 그대로 커버한다 — post_id가 앞이라
    -- "이 스무 글 중 내가 누른 것"을 글마다 인덱스 탐색 한 번으로 끝내고, 읽는 컬럼이 둘 다
    -- 인덱스 안에 있어 테이블을 보지 않는다.
    constraint uk_post_likes_post_user unique (post_id, user_id),
    -- 글이 지워지면 좋아요도 함께 사라진다(spec.md §2.4). 남겨 둘 이유가 없는 종속 데이터다.
    constraint fk_post_likes_post foreign key (post_id) references posts (id) on delete cascade,
    constraint fk_post_likes_user foreign key (user_id) references users (id) on delete cascade
) engine = InnoDB
  default charset = utf8mb4;

create table comments
(
    id         bigint       not null auto_increment,
    post_id    bigint       not null,
    author_id  bigint       not null,
    -- 대댓글이 없으므로 parent_id를 만들지 않는다. 쓰지 않을 컬럼은 부채다 —
    -- 있으면 조회마다 "이건 왜 항상 null인가"를 설명해야 하고, 언젠가 누가 채운다.
    content    varchar(500) not null,
    created_at datetime(6)  not null,
    updated_at datetime(6)  not null,
    primary key (id),
    constraint fk_comments_post foreign key (post_id) references posts (id) on delete cascade,
    -- 사람은 지워도 글은 남기는 정책이 아직 없다. users 삭제 경로가 생길 때 함께 정한다.
    constraint fk_comments_author foreign key (author_id) references users (id),
    constraint ck_comments_content_not_blank check (char_length(trim(content)) > 0)
) engine = InnoDB
  default charset = utf8mb4;

-- 글 하나의 댓글 목록. 정렬이 id desc라 인덱스도 그 방향이다(posts와 같은 규약).
create index idx_comments_post_id_desc on comments (post_id, id desc);
