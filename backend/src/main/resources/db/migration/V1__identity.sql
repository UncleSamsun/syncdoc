-- REQ-001 초대와 로그인. 테이블과 제약은 docs/03-tech-spec/data-model.md를 따른다.

create table users (
    id             uuid        primary key,
    github_user_id text        not null unique,
    login          text        not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null
);

create table invitations (
    id             uuid        primary key,
    github_user_id text        not null unique,
    granted_by     uuid        references users (id),
    granted_at     timestamptz not null,
    revoked_at     timestamptz
);

create table user_credentials (
    user_id                  uuid        primary key references users (id),
    access_token_ciphertext  text        not null,
    refresh_token_ciphertext text,
    expires_at               timestamptz,
    refresh_expires_at       timestamptz,
    key_version              integer     not null,
    version                  bigint      not null
);

create table sessions (
    id         uuid        primary key,
    user_id    uuid        not null references users (id),
    token_hash text        not null unique,
    expires_at timestamptz not null,
    created_at timestamptz not null
);

create index sessions_user_id_idx on sessions (user_id);
