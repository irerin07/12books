-- 탈퇴. **행을 지우지 않는다** — 이 프로젝트의 규약을 여기서도 지킨다.
--
-- 계획서는 하드 삭제를 권고했지만 그렇게 하지 않기로 했다. 지운 뒤에 오는 질문("이 사람이
-- 뭘 썼다가 지웠나", "지난달 통계가 왜 달라졌나")에 답할 수 없고, 무엇보다 되돌릴 방법이
-- 없다. 보관 기간은 1~3년이고, 그 뒤의 실제 파기와 개인정보 암호화는 별도 작업이다.

alter table users
    add column deleted_at datetime(6);

-- 행이 남으면 유니크 제약이 이메일과 handle을 붙잡는다. 같은 주소로 다시 가입할 수 없으면
-- 탈퇴가 **영구 추방**이 된다.
--
-- MySQL에는 부분 유니크 인덱스가 없으니 생성 컬럼으로 만든다 — 살아 있으면 값, 탈퇴하면
-- NULL. 유니크 인덱스가 NULL을 서로 다른 값으로 보므로 탈퇴한 행은 몇 개든 쌓인다.
-- readings.shelved_book_id와 같은 기법이다(V8).
alter table users
    add column active_email varchar(255) generated always as (if(deleted_at is null, email, null)) stored,
    add column active_handle varchar(20) generated always as (if(deleted_at is null, handle, null)) stored;

-- 새 제약을 먼저 만들고 옛 것을 뗀다. 순서를 뒤집으면 그 사이에 중복이 들어올 수 있다.
alter table users
    add constraint uk_users_active_email unique (active_email),
    add constraint uk_users_active_handle unique (active_handle);

alter table users drop index uk_users_email;
alter table users drop index uk_users_handle;
