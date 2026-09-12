-- 한 사람이 한 책을 여러 번 읽을 수 있게 한다.
--
-- 지금까지는 (user_id, book_id)가 유일했다. 그래서 뺐던 책을 다시 담으면 같은 행을 다시 꽂는
-- 수밖에 없었고, 예전 진도가 말없이 살아났다. 사용자가 "이어서 읽기"와 "새로 시작" 중에
-- 고를 수 있으려면 새로 시작이 **새 행**이어야 한다 — 그러지 않으면 고르는 순간 지난 기록을
-- 덮어쓰게 되고, 그건 이 프로젝트가 지우지 않기로 한 바로 그 데이터다.
--
-- 그래도 지켜야 할 규칙은 남는다: **서재에 꽂혀 있는 행은 (사람, 책)당 최대 하나.** 같은 책이
-- 서재에 두 줄로 보이면 진도를 어느 쪽에 적어야 할지 알 수 없다.
--
-- MySQL에는 부분 유니크 인덱스(where in_bookshelf)가 없다. 대신 생성 컬럼으로 같은 것을
-- 만든다 — 꽂혀 있으면 book_id, 빠져 있으면 NULL이다. 유니크 인덱스는 NULL을 서로 다른 값으로
-- 보므로 빠진 행은 몇 개든 쌓이고, 꽂힌 행만 (user_id, book_id)로 유일해진다.
--
-- 이 성질은 V7에서 deleted_at을 유니크 키에 넣으면 안 되는 이유였던 바로 그것이다. 그때는
-- 살아 있는 행의 유일성을 깨뜨려서 문제였고, 여기서는 NULL이 되는 쪽이 "유일하지 않아도 되는"
-- 쪽이라 방향이 맞아떨어진다.

alter table readings
    add column shelved_book_id bigint generated always as (if(in_bookshelf, book_id, null)) stored;

alter table readings
    add constraint uk_readings_user_shelved_book unique (user_id, shelved_book_id);

-- 옛 제약은 이제 틀렸다. 같은 책의 지난 독서가 여러 행으로 쌓이는 것을 막아 버린다.
alter table readings
    drop index uk_readings_user_book;

-- "이 책의 지난 독서 중 가장 최근 것"을 찾는 조회가 담기마다 돈다.
create index idx_readings_user_book_past on readings (user_id, book_id, in_bookshelf, id desc);
