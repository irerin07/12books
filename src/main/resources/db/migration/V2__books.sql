create table books
(
    id            bigint       not null auto_increment,
    isbn13        varchar(13),
    source_key    varchar(64),
    title         varchar(500) not null,
    authors       varchar(500) not null,
    publisher     varchar(200),
    thumbnail_url varchar(500),
    page_count    int,
    published_at  date,
    created_at    datetime(6)  not null,
    updated_at    datetime(6)  not null,
    primary key (id),
    -- 둘 다 nullable + unique. MySQL이 NULL 중복을 허용하므로 ISBN이 있는 책은 isbn13으로,
    -- 없는 책은 source_key로 각각 유일성을 지킨다.
    constraint uk_books_isbn13 unique (isbn13),
    constraint uk_books_source_key unique (source_key)
) engine = InnoDB
  default charset = utf8mb4;
