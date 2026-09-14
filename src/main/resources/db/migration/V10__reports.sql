-- 신고. 버튼만 있고 쌓이는 곳이 없으면 신고 기능을 만든 것이 아니다.

-- 권한 구분이 없었다. 관리자 경로가 생기므로 사람마다 역할을 갖는다.
--
-- 토큰이 아니라 이 컬럼을 매 요청 확인한다. 역할을 토큰에 실으면 권한을 뺏어도
-- 그 사람의 토큰이 만료될 때까지 관리자로 남는다 — 뺏는 이유를 생각하면 그 시차가
-- 가장 곤란한 순간에 열려 있는 셈이다.
alter table users
    add column role varchar(20) not null default 'USER';

create table reports
(
    id          bigint       not null auto_increment,
    reporter_id bigint       not null,
    -- POST · COMMENT · USER. 대상 테이블이 셋이라 FK를 걸 수 없다 — 그래서 존재 확인은
    -- 응용이 한다. 대신 target_type을 함께 저장해 어느 테이블의 id인지 잃지 않는다.
    target_type varchar(20)  not null,
    target_id   bigint       not null,
    reason      varchar(20)  not null,
    -- 신고자가 덧붙이는 말. 없어도 된다.
    detail      varchar(500),
    status      varchar(20)  not null,
    -- 처리한 운영자와 시각. 처리 전에는 둘 다 비어 있다.
    handled_by  bigint,
    handled_at  datetime(6),
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  not null,
    primary key (id),
    -- 같은 사람이 같은 대상을 여러 번 신고해도 한 건이다. 여러 건으로 쌓이면 한 사람이
    -- 신고 수를 부풀려 우선순위를 조작할 수 있다.
    constraint uk_reports_reporter_target unique (reporter_id, target_type, target_id),
    constraint fk_reports_reporter foreign key (reporter_id) references users (id),
    constraint fk_reports_handler foreign key (handled_by) references users (id)
) engine = InnoDB
  default charset = utf8mb4;

-- 운영자의 목록은 "처리 안 된 것부터, 최신순"이다. status를 앞에 두어 그것으로 좁힌 뒤
-- 그 안에서 바로 정렬된다.
create index idx_reports_status_id_desc on reports (status, id desc);
