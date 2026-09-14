-- 알림. 반응이 있었는데 본인이 모르면 반응이 없는 것과 같다.

create table notifications
(
    id           bigint      not null auto_increment,
    -- 알림을 받는 사람. 목록 조회가 전부 이 컬럼으로 좁힌다.
    recipient_id bigint      not null,
    -- 그 일을 한 사람. 본인 행동은 애초에 행을 만들지 않으므로 recipient와 같을 수 없다.
    actor_id     bigint      not null,
    type         varchar(30) not null,
    -- 무엇에 대한 알림인가. 팔로우에는 딸린 대상이 없어 둘 다 비어 있다.
    target_type  varchar(20),
    target_id    bigint,
    -- 읽은 시각. null이면 안 읽었다. boolean 대신 시각을 쓰는 것은 "언제 읽었나"가
    -- 공짜로 남기 때문이고, 삭제 플래그와 같은 이유다(CLAUDE.md 빈 값·삭제 규약).
    read_at      datetime(6),
    created_at   datetime(6) not null,
    updated_at   datetime(6) not null,
    primary key (id),
    -- 같은 일은 한 건만 남는다. 하트를 껐다 켰다 해도 알림이 쌓이지 않는다.
    --
    -- target_type·target_id가 null인 팔로우 알림은 이 제약으로 막히지 않는다 —
    -- 유니크 인덱스가 NULL을 서로 다른 값으로 보기 때문이다. 그래서 팔로우는
    -- target_type에 'USER', target_id에 상대 id를 넣어 null을 피한다.
    constraint uk_notifications_event
        unique (recipient_id, type, actor_id, target_type, target_id),
    constraint fk_notifications_recipient foreign key (recipient_id) references users (id),
    constraint fk_notifications_actor foreign key (actor_id) references users (id),
    -- 자기 자신에게 알리지 않는다. 응용에서 먼저 막지만 엔티티를 거치지 않는 쓰기가
    -- 생겨도 스키마가 마지막으로 막는다.
    constraint ck_notifications_not_self check (recipient_id <> actor_id)
) engine = InnoDB
  default charset = utf8mb4;

-- 목록은 "내 것만, 최신순"이다. 안 읽은 개수도 이 인덱스로 센다 — read_at을 뒤에 두면
-- recipient_id로 좁힌 뒤 그 안에서 바로 걸러진다.
create index idx_notifications_recipient_id_desc on notifications (recipient_id, id desc);
create index idx_notifications_unread on notifications (recipient_id, read_at);
