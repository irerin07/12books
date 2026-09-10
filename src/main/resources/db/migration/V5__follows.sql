-- 팔로우 관계. 여기서 제품이 SNS가 된다.
create table follows
(
    -- 대리 키를 둔다. 관계 자체는 (누가, 누구를)로 이미 유일해서 복합 PK로도 되지만,
    -- 그러면 목록에 쓸 단조 증가 키가 없어 커서 페이징과 최신순 정렬이 상대방 id를 빌려 쓰게 된다.
    -- 이 프로젝트의 모든 목록이 id 커서라는 규약(plan.md T5)과 나머지 테이블 전부가 bigint id라는
    -- 점을 따른다. 클러스터 인덱스 이점은 아래 유니크 인덱스가 그대로 대신한다.
    id          bigint      not null auto_increment,
    follower_id bigint      not null,
    followee_id bigint      not null,
    created_at  datetime(6) not null,
    updated_at  datetime(6) not null,
    primary key (id),
    -- 중복 팔로우를 막는 1차 방어선. 응용이 먼저 조회해서 막는 대신 DB가 막는다.
    --
    -- 동시에 피드가 매 요청 하는 조회("내가 팔로우하는 사람들")를 그대로 커버한다 —
    -- follower_id로 좁히고 followee_id만 읽으므로 두 컬럼이 다 인덱스 안에 있어 테이블을 보지 않는다.
    constraint uk_follows_follower_followee unique (follower_id, followee_id),
    constraint fk_follows_follower foreign key (follower_id) references users (id) on delete cascade,
    constraint fk_follows_followee foreign key (followee_id) references users (id) on delete cascade,
    -- 자기 자신 팔로우는 응용에서 400으로 막지만, 엔티티를 거치지 않는 쓰기가 생겨도
    -- 스키마가 마지막으로 막는다.
    constraint ck_follows_not_self check (follower_id <> followee_id)
) engine = InnoDB
  default charset = utf8mb4;

-- 팔로워 목록("나를 팔로우하는 사람들")을 최신순으로 준다. 위 유니크 인덱스는 follower_id가
-- 앞이라 이 방향을 돕지 못한다.
--
-- 반대 방향(팔로잉 목록)에는 같은 인덱스를 두지 않았다. 팔로워 수는 인기에 따라 얼마든지 늘지만
-- 팔로잉은 스스로 눌러서 늘리는 것이라 상한이 낮아, 정렬 비용이 문제가 되는 쪽은 이쪽뿐이다.
-- 필요해지면 그때 (follower_id, id desc)를 더한다.
create index idx_follows_followee_id_desc on follows (followee_id, id desc);
