-- 차단. 화면 기능이 아니라 도메인 정책이라 목록을 만드는 쿼리 전부에 조건이 붙는다(plan.md L2-2).
--
-- 보이지 않는 것은 **양방향**이다. A가 B를 차단하면 A도 B를 못 보고 B도 A를 못 본다.
-- 그래서 행은 한 방향(누가 눌렀나)만 기록하고, 조회가 두 방향을 모두 본다.
--
-- 팔로우는 **차단한 쪽만** 끊는다. A가 B를 차단하면 A→B는 사라지고 B→A는 남는다 —
-- 차단은 A의 의사이지 B의 의사를 지울 근거가 아니다. 남은 B→A는 조회에서 가려지고,
-- 차단이 풀리면 그대로 돌아온다.

create table blocks
(
    -- 대리 키를 둔다. 관계 자체는 (누가, 누구를)로 이미 유일하지만 목록에 쓸 단조 증가 키가
    -- 필요하다 — 이 프로젝트의 모든 목록이 id 커서다(CLAUDE.md, plan.md T5).
    id         bigint      not null auto_increment,
    blocker_id bigint      not null,
    blocked_id bigint      not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    -- 중복 차단을 막는 1차 방어선. 응용이 먼저 조회해서 막는 대신 DB가 막는다 —
    -- 동시에 들어온 두 요청은 함께 "없음"을 본다.
    constraint uk_blocks_blocker_blocked unique (blocker_id, blocked_id),
    constraint fk_blocks_blocker foreign key (blocker_id) references users (id) on delete cascade,
    constraint fk_blocks_blocked foreign key (blocked_id) references users (id) on delete cascade,
    -- 자기 차단은 응용에서 400으로 막지만, 엔티티를 거치지 않는 쓰기가 생겨도 스키마가
    -- 마지막으로 막는다. follows의 같은 제약과 짝이다.
    constraint ck_blocks_not_self check (blocker_id <> blocked_id)
) engine = InnoDB
  default charset = utf8mb4;

-- 조회가 두 방향을 본다: "내가 차단한 사람인가"와 "나를 차단한 사람인가".
-- uk가 앞 방향을 커버하므로 뒤 방향만 인덱스를 따로 둔다.
create index idx_blocks_blocked on blocks (blocked_id, blocker_id);
