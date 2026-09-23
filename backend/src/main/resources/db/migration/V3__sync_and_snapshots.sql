-- REQ-006 GitHub 변경 반영. 테이블과 제약은 docs/03-tech-spec/data-model.md를 따른다.
-- assets는 첨부를 다루는 TASK-006에서 만든다. 이 migration은 수집·게시·재시도에 필요한 것만 만든다.

-- 수집 작업 큐. worker가 SKIP LOCKED로 하나씩 잡고 임대 token으로 소유를 증명한다.
create table sync_jobs (
    id              uuid        primary key,
    project_id      uuid        not null references projects (id),
    kind            text        not null,
    state           text        not null,
    attempt         integer     not null,
    due_at          timestamptz not null,
    lease_until     timestamptz,
    lease_token     uuid,
    target_revision text,
    rerun_requested boolean     not null,
    last_error_code text,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    constraint sync_jobs_state_check check (state in ('queued', 'running', 'succeeded', 'failed', 'canceled')),
    constraint sync_jobs_attempt_check check (attempt >= 0)
);

-- 활성 작업은 프로젝트·종류마다 하나뿐이다. 중복 이벤트가 같은 작업을 두 번 예약하지 못한다.
create unique index sync_jobs_active_kind_idx
    on sync_jobs (project_id, kind)
    where state in ('queued', 'running');

create index sync_jobs_due_idx on sync_jobs (due_at) where state = 'queued';
create index sync_jobs_lease_idx on sync_jobs (lease_until) where state = 'running';
create index sync_jobs_project_idx on sync_jobs (project_id);

-- 시도 이력. 마지막 시도와 마지막 성공을 구분해 UI-008이 기준 시각을 보여줄 수 있게 한다.
create table sync_runs (
    id               uuid        primary key,
    project_id       uuid        not null references projects (id),
    job_id           uuid        not null references sync_jobs (id),
    started_at       timestamptz not null,
    finished_at      timestamptz,
    outcome          text        not null,
    source_revision  text,
    error_code       text,
    diagnostics_json text        not null,
    constraint sync_runs_outcome_check check (outcome in ('running', 'succeeded', 'failed'))
);

create index sync_runs_project_started_idx on sync_runs (project_id, started_at desc);
create index sync_runs_project_success_idx on sync_runs (project_id, finished_at desc) where outcome = 'succeeded';

-- 게시본. 서로 다른 revision의 문서를 한 snapshot에 섞지 않는다.
create table document_snapshots (
    id               uuid        primary key,
    project_id       uuid        not null references projects (id),
    source_revision  text        not null,
    renderer_version text        not null,
    policy_version   text        not null,
    created_at       timestamptz not null,
    complete         boolean     not null,
    constraint document_snapshots_identity_key
        unique (project_id, source_revision, renderer_version, policy_version)
);

-- projects(id, current_snapshot_id)가 참조할 대상이다. 다른 프로젝트의 snapshot을 걸지 못하게 한다.
create unique index document_snapshots_project_id_idx on document_snapshots (project_id, id);

alter table projects
    add constraint projects_current_snapshot_fk
    foreign key (id, current_snapshot_id)
    references document_snapshots (project_id, id);

create table documents (
    id             uuid    primary key,
    snapshot_id    uuid    not null references document_snapshots (id),
    path           text    not null,
    spec_id        text,
    kind           text,
    title          text    not null,
    source_hash    text    not null,
    -- html은 문서 변환을 만드는 TASK-006에서 채운다. 수집 단계에서는 원문만 보관한다.
    html           text,
    headings_json  text    not null,
    diagrams_json  text    not null,
    plain_text     text    not null,
    warnings_json  text    not null,
    state          text    not null,
    constraint documents_path_key unique (snapshot_id, path),
    constraint documents_state_check check (state in ('collected', 'invalid'))
);

-- tasks(snapshot_id, document_id)가 같은 snapshot 안의 문서만 가리키게 하는 복합 대상이다.
create unique index documents_snapshot_id_idx on documents (snapshot_id, id);
create unique index documents_spec_id_idx on documents (snapshot_id, spec_id) where spec_id is not null;

-- 서명을 검증한 delivery만 기록한다. raw payload와 토큰은 저장하지 않는다.
create table webhook_deliveries (
    delivery_id  text        primary key,
    event        text        not null,
    received_at  timestamptz not null,
    processed_at timestamptz
);

create index webhook_deliveries_received_idx on webhook_deliveries (received_at);
