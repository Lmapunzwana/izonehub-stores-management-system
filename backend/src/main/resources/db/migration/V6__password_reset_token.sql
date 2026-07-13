create table password_reset_token (
    id         uuid        primary key,
    token      varchar(128) not null unique,
    user_id    uuid        not null references app_users(id),
    expires_at timestamptz not null,
    used       boolean     not null default false
);

create index idx_prt_token on password_reset_token(token);
