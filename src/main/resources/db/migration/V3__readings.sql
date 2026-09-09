-- 내 서재. (user, book) 당 한 행이고, 재독은 새 행을 만들지 않고 상태를 되돌려 재사용한다.
create table readings
(
    id           bigint      not null auto_increment,
    user_id      bigint      not null,
    book_id      bigint      not null,
    status       varchar(20) not null,
    current_page int         not null default 0,
    -- 총 쪽수를 books가 아니라 여기 두는 이유: 카카오가 주지 않아 사용자가 채워야 하는데,
    -- 공용 books를 사용자가 고치게 하면 등록 서명으로 막은 오염 경로가 되살아난다.
    -- 판본마다 쪽수가 다르다는 점에서도 사용자별이 맞다.
    page_count   int,
    started_at   datetime(6),
    finished_at  datetime(6),
    rating       int,
    created_at   datetime(6) not null,
    updated_at   datetime(6) not null,
    primary key (id),
    constraint uk_readings_user_book unique (user_id, book_id),
    constraint fk_readings_user foreign key (user_id) references users (id),
    -- 책은 여러 사람의 서재에 담기는 독립 생명주기라 CASCADE가 아니라 RESTRICT다.
    constraint fk_readings_book foreign key (book_id) references books (id),
    constraint ck_readings_current_page check (current_page >= 0),
    constraint ck_readings_page_count check (page_count is null or page_count > 0),
    constraint ck_readings_rating check (rating is null or rating between 1 and 5)
) engine = InnoDB
  default charset = utf8mb4;

-- 서재 조회는 "그 사람의 서재를 상태로 거른다"가 기본 형태다.
create index idx_readings_user_status on readings (user_id, status);

-- 연간 목표. 설정하지 않은 해는 행이 없고, 조회 계층이 기본 12권으로 간주한다.
create table reading_goals
(
    id           bigint      not null auto_increment,
    user_id      bigint      not null,
    year         int         not null,
    target_count int         not null,
    created_at   datetime(6) not null,
    updated_at   datetime(6) not null,
    primary key (id),
    constraint uk_reading_goals_user_year unique (user_id, year),
    constraint fk_reading_goals_user foreign key (user_id) references users (id),
    constraint ck_reading_goals_target check (target_count between 1 and 1000),
    constraint ck_reading_goals_year check (year between 2000 and 2100)
) engine = InnoDB
  default charset = utf8mb4;

-- books.page_count를 걷어낸다. V2에서 만들었지만 카카오가 값을 주지 않아 한 번도 채워진 적이
-- 없고, readings.page_count가 생긴 지금 남겨두면 "책 쪽수는 어느 쪽인가"를 매번 되묻게 된다.
-- 전부 null이라 잃는 데이터가 없다.
alter table books
    drop column page_count;
