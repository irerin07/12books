-- 팔로우 관계. 여기서 제품이 SNS가 된다.
create table follows
(
    follower_id bigint      not null,
    followee_id bigint      not null,
    created_at  datetime(6) not null,
    updated_at  datetime(6) not null,
    -- 대리 키를 두지 않는다. 관계 자체가 (누가, 누구를)로 이미 유일하고, 이 복합 키가 곧
    -- "중복 팔로우 불가"라는 규칙이다. 응용이 먼저 조회해서 막는 대신 DB가 1차 방어선이다.
    --
    -- 순서도 의미가 있다. InnoDB는 이 PK가 곧 데이터 정렬 순서라 "내가 팔로우하는 사람들"
    -- 조회(follower_id로 좁힘)가 별도 인덱스 없이 끝난다. 피드가 매 요청 그 조회를 한다.
    primary key (follower_id, followee_id),
    constraint fk_follows_follower foreign key (follower_id) references users (id) on delete cascade,
    constraint fk_follows_followee foreign key (followee_id) references users (id) on delete cascade,
    -- 자기 자신 팔로우는 응용에서 400으로 막지만, 엔티티를 거치지 않는 쓰기가 생겨도
    -- 스키마가 마지막으로 막는다.
    constraint ck_follows_not_self check (follower_id <> followee_id)
) engine = InnoDB
  default charset = utf8mb4;

-- 반대 방향 조회("나를 팔로우하는 사람들")는 PK 앞자리를 못 써서 이 인덱스가 필요하다.
-- InnoDB의 보조 인덱스는 PK 컬럼을 뒤에 달고 다니므로 사실상 (followee_id, follower_id)가 되고,
-- 팔로워 목록의 정렬·커서가 그대로 이 인덱스를 탄다.
create index idx_follows_followee on follows (followee_id);
