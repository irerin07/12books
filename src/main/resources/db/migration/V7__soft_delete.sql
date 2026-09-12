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
-- 서재의 "빼기"는 삭제가 아니다. 담았다 뺐다 하는 것은 정상적인 사용이고, 다시 담기는
-- 복구가 아니라 그냥 다음 행동이다. 같은 컬럼 이름을 쓰면 되살리기 코드가 예외처럼 읽힌다.
--
-- deleted_at은 여기 두지 않는다. 지금 readings를 진짜로 지우는 경로가 없어서 항상 null인
-- 컬럼이 되고, 그러면 모든 조회가 아무것도 거르지 않는 조건 하나를 더 달게 된다. 계정 탈퇴나
-- 개인정보 파기 경로가 생길 때 posts·comments·users와 함께 정한다.
alter table readings add column in_bookshelf boolean not null default true;

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

create index idx_readings_user_live on readings (user_id, in_bookshelf, id desc);
create index idx_readings_user_status_live on readings (user_id, in_bookshelf, status);
drop index idx_readings_user_id_desc on readings;
drop index idx_readings_user_status on readings;

-- uk_readings_user_book(user_id, book_id)은 그대로 둔다. 한 사람이 한 책에 갖는 기록은
-- 서재에 있든 없든 하나이고, 담기/빼기는 그 행의 in_bookshelf를 켜고 끄는 일이다.
-- 기록이 사라지지 않으므로 이 제약은 이제 예외 없이 참이다.
