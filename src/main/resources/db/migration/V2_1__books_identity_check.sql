-- isbn13과 source_key 중 정확히 하나만 있어야 한다.
--
-- 둘 다 비면 두 unique 제약이 모두 NULL을 허용하므로 어느 쪽으로도 유일성이 지켜지지 않고,
-- 같은 책이 무한히 쌓인다. 둘 다 있으면 같은 책이 서로 다른 두 키로 각각 등록될 수 있다.
--
-- Book.Builder가 같은 것을 막지만 그것은 JPA를 지나는 경로에서만이다. 배치나 JDBC처럼
-- 엔티티를 거치지 않는 쓰기가 생겨도 스키마가 마지막으로 막는다.
--
-- V2를 고치지 않고 새 버전을 더한다 — 이미 적용된 마이그레이션을 수정하면 체크섬이 어긋난다.
alter table books
    add constraint ck_books_identity check ((isbn13 is null) <> (source_key is null));
