-- 운영자 숨김. 작성자 삭제와 다른 사건이라 deleted_at을 재활용하지 않는다.
--
-- 합치면 "작성자가 지운 글"과 "운영자가 내린 글"을 구분할 수 없다. 둘은 물어야 할 질문이
-- 다르고(누가 왜 내렸나, 되돌릴 수 있나), 무엇보다 신고가 기각됐을 때 되돌릴 수가 없다 —
-- 되돌리는 순간 작성자가 지운 글까지 함께 살아난다.
--
-- boolean이 아니라 시각인 것은 "언제 내렸나"가 공짜로 남기 때문이다. 신고 처리 이력과
-- 대조해야 하는 값이라 특히 그렇다.
alter table posts
    add column hidden_at datetime(6);

alter table comments
    add column hidden_at datetime(6);
