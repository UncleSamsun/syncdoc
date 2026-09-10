---
id: DOC-011
type: tech-data
status: 확정
---

# MVP 데이터 모델

SQL migration은 아직 구현하지 않았다. 이 문서는 구현 대상 스키마다.

근거: [API 계약](api-spec.md), [MVP 요구](../01-prd/mvp-scope.md). PostgreSQL 사용. ID는 UUID PK, 외부 GitHub ID는 text unique, 시각은 timestamptz. 아래 필드는 `?`만 NULL 허용하며 나머지는 NOT NULL이다. enum 값은 애플리케이션과 DB CHECK를 맞춘다.

## 테이블

| 테이블 | 주요 필드 | 제약·관계 |
|---|---|---|
| users | id, github_user_id, login, created_at, updated_at | github_user_id unique. login은 표시용으로 변경 가능 |
| invitations | id, github_user_id, granted_by?, granted_at, revoked_at? | github_user_id unique. granted_by→users; 최초 관리자만 null 허용. 재초대 시 같은 항목 갱신 |
| user_credentials | user_id, access_token_ciphertext, refresh_token_ciphertext?, expires_at?, refresh_expires_at?, key_version, version | user_id PK/FK→users. 토큰 암호화키는 DB 밖. refresh는 낙관적 잠금으로 중복 회전 방지 |
| sessions | id, user_id, token_hash, expires_at, created_at | user_id→users, token_hash unique. 원시 쿠키값 미저장 |
| github_installations | id, github_installation_id, owner_github_id, status, updated_at | 외부 installation ID unique. 실제 private key는 배포 secret에 저장 |
| projects | id, github_repository_id, full_name, installation_id, created_by, branch, docs_root, github_project_node_id?, current_snapshot_id?, version, created_at | repository ID unique, installation_id→github_installations, created_by→users. snapshot 복합 FK 아래 참고 |
| sync_jobs | id, project_id, kind, state, attempt, due_at, lease_until?, lease_token?, target_revision?, rerun_requested, last_error_code?, created_at | project_id→projects. queued/running 활성 kind별 partial unique(project_id,kind). state CHECK |
| sync_runs | id, project_id, job_id, started_at, finished_at?, outcome, source_revision?, error_code?, diagnostics_json | project_id→projects, job_id→sync_jobs. latest attempt와 latest success를 구분. diagnostics는 오류 경로/코드만 보존 |
| document_snapshots | id, project_id, source_revision, renderer_version, policy_version, created_at, complete | project_id→projects. unique(project_id,source_revision,renderer_version,policy_version), unique(project_id,id) |
| documents | id, snapshot_id, path, spec_id?, kind?, title, source_hash, html?, headings_json, diagrams_json, plain_text, warnings_json, state | snapshot_id→document_snapshots. unique(snapshot_id,path), spec_id not null일 때 unique(snapshot_id,spec_id). invalid 문서는 html null |
| assets | id, snapshot_id, path, mime, bytes_hash, storage_key, byte_size | snapshot_id→document_snapshots. unique(snapshot_id,path), byte_size>=0 |
| tasks | id, snapshot_id, task_spec_id, document_id, anchor, confirmed, source_refs_json, validation_refs_json, github_issue_node_id? | snapshot_id→document_snapshots. document는 동일 snapshot 복합 FK. unique(snapshot_id,task_spec_id) |
| github_issue_snapshots | id, project_id, github_issue_node_id, number, title, state, state_reason?, assignees_json, labels_json, linked_prs_json, observed_at | project_id→projects. unique(project_id,github_issue_node_id). GitHub가 정본인 파생 정보 |
| webhook_deliveries | delivery_id, event, received_at, processed_at? | delivery_id PK. 서명 검증 후 저장. raw payload·토큰 미저장 |

projects(project_id=id,current_snapshot_id)는 document_snapshots(project_id,id)를 참조해 다른 프로젝트 snapshot을 연결하지 못하게 한다. projects 생성 후 snapshots를 만들고 current_snapshot_id FK를 추가하는 migration 순서를 사용한다. documents에도 unique(snapshot_id,id)를 두고 tasks(snapshot_id,document_id)가 참조한다. 삭제는 명시적인 정리 작업으로 하며 프로젝트에서 무제한 cascade 삭제하지 않는다.

## 소유와 파생 데이터

서비스 고유 정본은 초대·세션·연결 설정이다. MD와 GitHub 원문은 GitHub에 남고, 문서/작업/Issue snapshot은 다시 만들 수 있다. 원문 조회는 source_revision과 path로 식별한다. 서비스에서 MD를 편집하거나 파생 결과로 원문을 덮어쓰지 않는다.

GitHub Project 상태·목표일·숨겨진 다른 저장소 항목은 공용 테이블에 복제하지 않는다. 첫 MVP는 사용자 자격증명으로 요청 시 조회한다. 성능 측정 뒤 사용자+Project별 캐시를 도입할 수 있지만 별도 권한 철회 설계가 필요하다.

작업-Issue 연결은 Issue 본문의 서비스 관리 메타데이터 영역(작업 ID·명세 경로·기준 revision) 한 곳을 외부 정본으로 제안한다. tasks.github_issue_node_id는 이를 읽은 파생값이다. 한 작업 ID에 여러 Issue가 주장되면 mapping_conflict로 표시하고 임의 선택하지 않는다. Issue 번호와 TASK ID는 분리한다.

## 저장과 갱신

동기화가 전체 문서를 준비한 뒤 complete=true인 snapshot으로 current_snapshot_id를 한 트랜잭션에서 교체한다. 파싱/정화/중복 ID 오류가 있으면 새 게시본으로 전환하지 않고 실패 기록을 남긴다. 최초 오류도 빈 성공으로 처리하지 않는다.

worker는 SKIP LOCKED로 due 작업 하나를 잡고 임대 token을 부여한다. 30초 heartbeat, 2분 임대를 제안한다. 완료 시 같은 lease_token인지 검사하고 재시도 작업의 결과를 오래된 worker가 덮어쓰지 못하게 한다. 활성 작업 중 이벤트는 rerun_requested로 합치고 완료 후 최신 revision 재확인 작업을 예약한다. 최소 재시도60초/최대15분,429는 GitHub 재시도 시각을 우선한다.

첨부는 실행 불가한 데이터로 취급한다. 최초 지원은 PNG/JPEG/WebP/GIF, 자산당10MB, 문서당1MB·최대1,000문서를 제안한다. SVG/HTML 자산·symlink·경로 이탈은 제한한다. 이 수치는 제안값이며 부하 검증 후 조정한다.

## 보관

초대 취소는 세션과 자격증명을 즉시 무효화한다. snapshots는 현재본+최근 성공2개를 기본 보관 제안으로 하고, 지워진 revision 요청은410. 세션은 만료 후24시간, delivery는7일, sync_runs는30일 후 정리한다. 현재본을 정리 작업이 지우지 않도록 트랜잭션/참조 검사를 한다. 백업은 서비스 고유 정보·암호화키의 별도 보관을 포함하며 파생 캐시 백업은 선택이다.
