-- REQ-002 프로젝트 연결. 테이블과 제약은 docs/03-tech-spec/data-model.md를 따른다.

create table github_installations (
    id                     uuid        primary key,
    github_installation_id text        not null unique,
    owner_github_id        text        not null,
    status                 text        not null,
    updated_at             timestamptz not null
);

create table projects (
    id                     uuid        primary key,
    github_repository_id   text        not null unique,
    full_name              text        not null,
    installation_id        uuid        not null references github_installations (id),
    created_by             uuid        not null references users (id),
    branch                 text        not null,
    docs_root              text        not null,
    github_project_node_id text,
    -- document_snapshots는 TASK-005의 V3에서 만든다. 복합 외래키는 그때 건다.
    current_snapshot_id    uuid,
    version                bigint      not null,
    created_at             timestamptz not null
);

create unique index projects_id_snapshot_idx on projects (id, current_snapshot_id);
create index projects_created_by_idx on projects (created_by);
