create table users
(
    id            bigint       not null auto_increment,
    email         varchar(255) not null,
    password_hash varchar(255) not null,
    handle        varchar(20)  not null,
    display_name  varchar(50)  not null,
    bio           varchar(200),
    avatar_url    varchar(500),
    created_at    datetime(6)  not null,
    updated_at    datetime(6)  not null,
    primary key (id),
    constraint uk_users_email unique (email),
    constraint uk_users_handle unique (handle)
) engine = InnoDB
  default charset = utf8mb4;
