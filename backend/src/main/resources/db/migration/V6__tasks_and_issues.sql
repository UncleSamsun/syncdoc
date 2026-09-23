-- REQ-003 현황과 작업. 명세가 정의한 작업과 GitHub가 가진 실행 상태를 따로 담는다.

-- 명세에서 뽑은 작업. 게시본에 묶이므로 문서가 바뀌면 작업 목록도 그 게시본 기준으로 바뀐다.
create table tasks (
    id                   uuid    primary key,
    snapshot_id          uuid    not null references document_snapshots (id),
    task_spec_id         text    not null,
    document_id          uuid    not null,
    anchor               text    not null,
    title                text    not null,
    -- 작업계획 문서에 있는 작업이 확정 작업이다. 후보와 취소는 분모에서 따로 다룬다.
    confirmed            boolean not null,
    source_refs_json     text    not null,
    validation_refs_json text    not null,
    -- GitHub가 정본인 파생값이다. 제목 접두사로 이어 붙이며 충돌하면 비워 둔다.
    github_issue_node_id text,
    mapping_conflict     boolean not null,
    constraint tasks_spec_key unique (snapshot_id, task_spec_id),
    -- 다른 게시본의 문서를 가리키지 못하게 한다.
    constraint tasks_document_fk foreign key (snapshot_id, document_id)
        references documents (snapshot_id, id)
);

create index tasks_snapshot_idx on tasks (snapshot_id);

-- GitHub에서 읽은 Issue. 정본은 GitHub이고 여기 있는 것은 관찰 시각이 붙은 사본이다.
create table github_issue_snapshots (
    id                   uuid        primary key,
    project_id           uuid        not null references projects (id),
    github_issue_node_id text        not null,
    number               integer     not null,
    title                text        not null,
    state                text        not null,
    state_reason         text,
    task_spec_id         text,
    assignees_json       text        not null,
    labels_json          text        not null,
    linked_prs_json      text        not null,
    observed_at          timestamptz not null,
    constraint github_issue_node_key unique (project_id, github_issue_node_id),
    constraint github_issue_state_check check (state in ('open', 'closed'))
);

create index github_issue_task_idx on github_issue_snapshots (project_id, task_spec_id);

-- Issue를 언제 어디까지 읽었는지. 집계가 확정인지 판단하는 근거이며, 못 읽었다는 사실을
-- 숨기면 불완전한 값이 확정된 값처럼 보인다.
alter table projects add column issues_observed_at timestamptz;
alter table projects add column issues_complete boolean not null default false;
