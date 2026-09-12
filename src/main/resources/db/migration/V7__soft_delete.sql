-- 삭제를 행 제거가 아니라 플래그로 바꾼다.
--
-- 지운 뒤에 오는 질문들이 있다 — "잘못 지웠으니 되살려 달라", "이 사람이 뭘 썼다가 지웠나",
-- "지난달 통계가 왜 달라졌나". 행을 지우면 이 질문에 답할 방법이 없고, 백업에서 한 행만
-- 꺼내 오는 일은 실무에서 사실상 불가능하다.
--
-- boolean 대신 시각을 쓴다. "지워졌는가"는 null 여부로 똑같이 답하면서 "언제"까지 남는데,
-- 컬럼을 하나 더 쓰지 않는다. 되살릴 때는 null로 되돌린다.

alter table posts add column deleted_at datetime(6) null;
alter table comments add column deleted_at datetime(6) null;
alter table readings add column deleted_at datetime(6) null;

-- 목록 조회가 전부 "안 지워진 것만"으로 좁혀지므로 기존 인덱스 앞에 그 조건이 붙는다.
-- MySQL에는 부분 인덱스가 없어 조건을 인덱스에 담을 수 없고, 대신 선행 컬럼으로 넣는다.
--
-- 기존 인덱스를 놔둔 채 더하지 않고 바꾼다 — 같은 컬럼으로 시작하는 인덱스가 둘이면
-- 옵티마이저가 고를 뿐 둘 다 유지 비용을 낸다.
--
-- 반드시 새 인덱스를 만든 뒤에 옛 것을 지운다. author_id·book_id·post_id는 외래 키 컬럼이라
-- 인덱스가 하나도 없는 순간이 있으면 MySQL이 DROP을 거절한다("needed in a foreign key
-- constraint"). 새 인덱스가 같은 컬럼으로 시작하므로 그것이 FK의 요구를 그대로 물려받는다.
create index idx_posts_author_live on posts (author_id, deleted_at, id desc);
create index idx_posts_book_live on posts (book_id, deleted_at, id desc);
drop index idx_posts_author_id_desc on posts;
drop index idx_posts_book_id_desc on posts;

create index idx_comments_post_live on comments (post_id, deleted_at, id desc);
drop index idx_comments_post_id_desc on comments;

create index idx_readings_user_live on readings (user_id, deleted_at, id desc);
create index idx_readings_user_status_live on readings (user_id, deleted_at, status);
drop index idx_readings_user_id_desc on readings;
drop index idx_readings_user_status on readings;

-- uk_readings_user_book(user_id, book_id)은 그대로 둔다.
--
-- 지운 행이 남으므로 같은 책을 다시 담으면 이 제약에 걸린다. 제약을 푸는 대신 다시 담기를
-- "되살리기"로 처리한다 — 한 사람이 한 책을 서재에 두 번 갖지 않는다는 규칙이 이 제약의
-- 본뜻이고, 그 규칙은 지운 뒤에도 옳다. 제약에 deleted_at을 더하는 방법은 MySQL에서 통하지
-- 않는다: 유니크 인덱스는 NULL을 서로 다른 값으로 보므로 (user, book, NULL)이 여러 벌
-- 허용되어 정작 살아 있는 행의 유일성이 깨진다.
