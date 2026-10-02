---
id: DOC-011
type: tech-data
status: 확정
---

# 데이터 설계

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
| projects | id, github_repository_id, full_name, installation_id, created_by, branch, docs_root, github_project_node_id?, current_snapshot_id?, issues_observed_at?, issues_complete, version, created_at | repository ID unique, installation_id→github_installations, created_by→users. snapshot 복합 FK 아래 참고 |
| sync_jobs | id, project_id, kind, state, attempt, due_at, lease_until?, lease_token?, target_revision?, rerun_requested, last_error_code?, created_at | project_id→projects. queued/running 활성 kind별 partial unique(project_id,kind). state CHECK |
| sync_runs | id, project_id, job_id, started_at, finished_at?, outcome, source_revision?, error_code?, diagnostics_json | project_id→projects, job_id→sync_jobs. latest attempt와 latest success를 구분. diagnostics는 오류 경로/코드만 보존 |
| document_snapshots | id, project_id, source_revision, renderer_version, policy_version, created_at, complete, checklist_json, traceability_json? | project_id→projects. unique(project_id,source_revision,renderer_version,policy_version), unique(project_id,id). checklist_json은 그 revision의 규칙 파일로 판정한 산출물 체크리스트(API-025) |
| documents | id, snapshot_id, path, spec_id?, kind?, title, source_hash, html?, headings_json, diagrams_json, links_json, plain_text, warnings_json, state | snapshot_id→document_snapshots. unique(snapshot_id,path), spec_id not null일 때 unique(snapshot_id,spec_id). invalid 문서는 html null |
| assets | id, snapshot_id, path, mime, bytes_hash, storage_key, byte_size | snapshot_id→document_snapshots. unique(snapshot_id,path), byte_size>=0. mime은 허용 목록 CHECK |
| asset_contents | storage_key, bytes, byte_size, created_at | storage_key는 내용 해시다. assets.storage_key→asset_contents. 같은 그림이 여러 게시본에 나와도 바이트는 한 벌만 남는다 |
| tasks | id, snapshot_id, task_spec_id, document_id, anchor, confirmed, source_refs_json, validation_refs_json, github_issue_node_id? | snapshot_id→document_snapshots. document는 동일 snapshot 복합 FK. unique(snapshot_id,task_spec_id) |
| github_issue_snapshots | id, project_id, github_issue_node_id, number, title, state, state_reason?, assignees_json, labels_json, linked_prs_json, observed_at | project_id→projects. unique(project_id,github_issue_node_id). GitHub가 정본인 파생 정보 |
| webhook_deliveries | delivery_id, event, received_at, processed_at? | delivery_id PK. 서명 검증 후 저장. raw payload·토큰 미저장 |

projects(project_id=id,current_snapshot_id)는 document_snapshots(project_id,id)를 참조해 다른 프로젝트 snapshot을 연결하지 못하게 한다. projects 생성 후 snapshots를 만들고 current_snapshot_id FK를 추가하는 migration 순서를 사용한다. documents에도 unique(snapshot_id,id)를 두고 tasks(snapshot_id,document_id)가 참조한다. 삭제는 명시적인 정리 작업으로 하며 프로젝트에서 무제한 cascade 삭제하지 않는다.

### 산출물 체크리스트

판정은 수집할 때 한 번 하고 게시본에 함께 둔다. 별도 표를 두지 않는다. 조회 단위가 게시본 하나이고 항목별로 질의할 요구가 없다.

수집 중에는 문서 원문이 손에 있지만 게시본에는 변환 결과만 남는다. 조회 시점에 다시 판정하려면 원문을 따로 저장해야 하고, 같은 게시본이 때에 따라 다른 판정을 내게 된다. 게시본은 불변이므로 판정도 그 revision에 고정한다.

규칙 파일은 저장소 루트의 `rules/spec-format.json`과 `rules/project-settings.md`다. 문서 경로 설정과 무관한 고정 경로이며, 읽지 못하면 그 사실을 미검사로 담는다. 판정을 비워 두고 통과로 보이게 하지 않는다.

### 연결 해제의 삭제 순서

외래키가 가리키는 반대 방향으로 지운다. `tasks` → `documents` → `assets` → `github_issue_snapshots` → `sync_runs` → `sync_jobs` → `projects.current_snapshot_id`를 비움 → `document_snapshots` → `projects` 순이다. 이력이 작업을 가리키므로(`sync_runs.job_id`) 이력을 먼저 지운다. `projects`가 `document_snapshots`를 가리키고 `document_snapshots`가 `projects`를 가리키므로 게시본을 지우기 전에 현재 게시본 참조를 먼저 끊는다.

`asset_contents`는 내용 해시가 열쇠라 여러 게시본이 같은 행을 가리킨다. 프로젝트 하나를 끊는다고 지우면 다른 프로젝트의 그림이 깨진다. 가리키는 `assets`가 하나도 남지 않은 행만 지운다.

`webhook_deliveries`는 프로젝트에 매이지 않는다. 중복 delivery를 막는 기록이므로 남긴다.

## 소유와 파생 데이터

서비스 고유 정본은 초대·세션·연결 설정이다. MD와 GitHub 원문은 GitHub에 남고, 문서/작업/Issue snapshot은 다시 만들 수 있다. 원문 조회는 source_revision과 path로 식별한다. 서비스에서 MD를 편집하거나 파생 결과로 원문을 덮어쓰지 않는다.

GitHub Project 상태·목표일·숨겨진 다른 저장소 항목은 공용 테이블에 복제하지 않는다. 첫 MVP는 사용자 자격증명으로 요청 시 조회한다. 성능 측정 뒤 사용자+Project별 캐시를 도입할 수 있지만 별도 권한 철회 설계가 필요하다.

작업-Issue 연결은 **Issue 제목의 `TASK-NNN:` 접두사**로 한다(2026-09-21 사용자 확정). 이 저장소의 Issue가 이미 쓰는 형식이라 추가 규약 없이 동작하고, 사람이 Issue 목록에서도 어느 작업인지 바로 읽는다. tasks.github_issue_node_id는 이를 읽은 파생값이다. 한 작업 ID에 여러 Issue가 주장되면 mapping_conflict로 표시하고 임의 선택하지 않는다. Issue 번호와 TASK ID는 분리한다.

검토했지만 채택하지 않은 안: Issue 본문에 서비스 관리 메타데이터 영역(작업 ID·명세 경로·기준 revision)을 두는 방식. 제목을 고쳐도 연결이 유지되고 기준 revision까지 묶을 수 있지만, 기존 Issue를 모두 고쳐야 하고 블록 형식을 규칙 파일에 새로 확정해야 한다. 제목 규칙이 실제로 부족해지면 그때 다시 본다.

## 저장과 갱신

links_json은 문서 안의 링크를 서비스 경로로 바꾼 결과다. 링크 해소는 같은 snapshot의 다른 문서를 알아야 가능하므로 변환 시점에 한 번 만들어 보관하고 조회 때 다시 계산하지 않는다.

동기화가 전체 문서를 준비한 뒤 complete=true인 snapshot으로 current_snapshot_id를 한 트랜잭션에서 교체한다. 파싱/정화/중복 ID 오류가 있으면 새 게시본으로 전환하지 않고 실패 기록을 남긴다. 최초 오류도 빈 성공으로 처리하지 않는다.

worker는 SKIP LOCKED로 due 작업 하나를 잡고 임대 token을 부여한다. 30초 heartbeat, 2분 임대를 제안한다. 완료 시 같은 lease_token인지 검사하고 재시도 작업의 결과를 오래된 worker가 덮어쓰지 못하게 한다. 활성 작업 중 이벤트는 rerun_requested로 합치고 완료 후 최신 revision 재확인 작업을 예약한다. 최소 재시도60초/최대15분,429는 GitHub 재시도 시각을 우선한다.

첨부 내용의 저장 배치는 2026-09-21 사용자 승인으로 DB에 둔다(`asset_contents`). 자산당 10MB 제안값이면 감당되고 백업·권한 검사가 한 곳에 모인다. 부하를 확인한 뒤 파일·객체 저장소로 옮길 수 있으며 그때 `storage_key`의 뜻만 바뀐다.

첨부는 실행 불가한 데이터로 취급한다. 최초 지원은 PNG/JPEG/WebP/GIF, 자산당10MB, 문서당1MB·최대1,000문서를 제안한다. SVG/HTML 자산·symlink·경로 이탈은 제한한다. 이 수치는 제안값이며 부하 검증 후 조정한다.

## 보관

초대 취소는 세션과 자격증명을 즉시 무효화한다. snapshots는 현재본+최근 성공2개를 기본 보관 제안으로 하고, 지워진 revision 요청은410. 세션은 만료 후24시간, delivery는7일, sync_runs는30일 후 정리한다. 현재본을 정리 작업이 지우지 않도록 트랜잭션/참조 검사를 한다. 백업은 서비스 고유 정보·암호화키의 별도 보관을 포함하며 파생 캐시 백업은 선택이다.

## 요구–작업 관계 보고서

[REQ-009](../01-prd/mvp-scope.md)의 revision별 파생 결과다.

### 분석 입력과 규약

#### 입력

동일 revision에서 성공적으로 수집한 문서의 documentId, 저장소 경로, 원문 Markdown, frontmatter, 렌더러 headings를 입력으로 받는다. 원문은 분석 후 메모리에서 버리고 기존 저장 정책을 바꾸지 않는다. 코드 블록·인라인 코드·HTML·예시 문서의 ID는 관계로 세지 않는다.

확정 `prd-requirements`에서 `## REQ-NNN 제목`, 확정 `tasks`에서 `## TASK-NNN 제목`을 정의로 읽는다. 초안·검토·폐기 및 proposal/guide/record는 정의와 관계 추출에서 제외한다. 문서 ID는 DOC, 항목 ID는 REQ/TASK로 별도 취급한다. 문서 자체의 기존 C0/C1/C2 검사 결과는 바꾸지 않는다.

#### 관계 추출

TASK 섹션의 `**근거:**` 문단에서 일반 텍스트의 REQ-NNN, REQ ID를 담은 링크 글자, 목록과 같은 접두어의 범위를 읽는다. `REQ-001 ~ REQ-008`은 양끝 포함, 오름차순, 동일 접두어, 세 자리 ID일 때만 펼친다. REQ-000·역순·접두어 혼합·네 자리 ID는 유효 관계로 만들지 않고 `UNSUPPORTED_REFERENCE`로 보고한다. 코드 안 ID는 무시한다. REQ ID가 전혀 없는 근거는 `TASK_WITHOUT_REQUIREMENT` 정보로 보고한다. 상위 요구 관계가 없는 문서 작업도 있으므로 오류로 단정하지 않는다.

관계의 방향은 TASK → REQ이다. `(projectId, snapshotId, itemId)`로 이름 공간을 구분한다. 근거에 같은 ID가 여러 번 있으면 관계는 하나로 합치고 최초 근거 위치를 남긴다. 링크에 경로가 있으면 정규화한 대상 문서가 같은 snapshot에서 해당 REQ 정의를 갖는지도 확인한다. 경로 밖 이동·외부 URL은 링크를 요청하지 않는다. GitHub URL은 첫 범위에서 ID 관계만 해소하고 URL 목적지는 검증하지 않는다.

중복 REQ/TASK는 `DUPLICATE_ITEM_ID`, 없는 REQ는 `MISSING_REQUIREMENT`, 로컬 링크 경로와 ID가 다른 문서에 대응하면 `REFERENCE_TARGET_MISMATCH`다. 링크 fragment 도달성은 첫 범위의 검사 대상이 아니다. 이동 링크에는 렌더러가 생성한 실제 heading id를 사용한다.

#### 진단과 상태

보고서 상태는 `complete`(전 범위 분석), `partial`(해석 불가 입력 포함), `unchecked`(분석 전)다. 상태는 요구 충족이나 승인 여부를 뜻하지 않는다. 진단은 code/severity/documentId/path/line/itemId/targetId/message를 갖고 원문 전체는 포함하지 않는다. 동일 code·위치·대상은 중복 제거한다.

중복 ID, 깨진 구조·메타데이터, 해석 불가 참조로 전체 관계를 신뢰할 수 없으면 partial이다. 근거 라벨이 없거나 비어 있어도 partial이며 `TASK_EVIDENCE_UNREADABLE`로 표시한다. 부분 분석에서는 연결이 확인된 요구만 `linked`로 표시하고 나머지는 `unknown`으로 둔다. complete에서만 관계가 없는 요구를 `unlinked`로 판정한다. 없는 ID와 명백한 경로 불일치는 완전하게 확인한 오류이므로 그 자체로 partial을 만들지는 않는다.

확정 요구 문서가 없으면 보고서는 `unchecked`, 이유는 `NO_CONFIRMED_REQUIREMENTS`다. 확정 작업 문서가 없으면 `unchecked`/`NO_CONFIRMED_TASKS`로 표시하고 요구는 unknown이다. 기존 체크리스트의 필수 문서 누락과 별도다. REQ/TASK 대상 종류를 선언한 문서의 status가 빠졌거나 허용 값이 아니면 의도적 초안 제외로 처리하지 않고 partial 진단을 남긴다.

### 저장과 게시

migration은 `V8__spec_traceability.sql`이다. document_snapshots에 nullable `traceability_json`을 추가한다. NULL은 기존 게시본의 미분석이고, 새 보고서는 schemaVersion=1을 포함한다.

보고서는 requirements, tasks, edges, findings, analysisStatus, uncheckedReason를 담는다. 항목은 itemId/title/documentId/path/anchor/line, 관계는 taskId/requirementId/sourceLocation을 갖는다. projectId/snapshotId/sourceRevision은 snapshot 정본에서 읽고 JSON에 중복 저장하지 않는다.

관계 계산은 모든 원문을 읽은 뒤, 현재 checklist 저장과 snapshot 완료·원자적 게시 전 수행한다. 문서의 참조 오류는 진단 결과로 게시할 수 있지만 분석기 예외·직렬화 실패는 수집 실패로 처리해 마지막 정상 게시본을 유지한다. 회수·삭제 시 snapshot과 함께 지워지며 별도 정리가 필요하지 않다.

분석 버전이 바뀌면 DocumentVersions의 기존 정책 버전을 올려 같은 commit도 새 분석 게시본을 만들도록 한다. 기존 게시본을 조회하면서 소급 수정하지 않는다. 현재 최대 문서 수·파일 크기 제한을 유지한다.


중복 TASK는 작업 매핑에서 임의 선택하지 않는다. 수집된 전체 원문에서 분석한 뒤 유일한 TASK 정의만 기존 tasks 테이블에 담는다. 따라서 중복 작업은 관계 진단에 남고 Issue 상태는 합성하지 않는다.

## 게시본 비교 파생 자료

REQ-010의 저장·비교 규약이다.

### 수집과 저장

V9__snapshot_comparison.sql migration을 적용한다. nullable `collection_branch`, `collection_docs_root`, `comparison_json`을 snapshot에 추가한다. NULL은 기존 게시본의 자료 부족이다. 새 수집은 실제로 사용한 branch/docsRoot를 처음부터 고정한다.

`document_snapshots_identity_key`는 projectId/revision/renderer/policy/collectionBranch/collectionDocsRoot로 확장하고 repository 조회도 같은 값을 쓴다. 기존 project/currentSnapshot 복합 FK는 유지한다. policy 버전을 올려 새 수집부터 scope·비교 자료를 채운다. 이전 완료 게시본은 그대로 둔다. 새 필드가 한쪽만 비거나 잘못되면 미확인이지 같은 범위로 간주하지 않는다.

comparison_json은 schemaVersion=1, fingerprintAlgorithm=`markdown-section-lf-v1`, indexStatus=complete/partial, 문서의 작성 상태, 항목 목록과 진단을 담는다. 항목은 kind=req/task, itemId, documentId, title, anchor, line, sectionHash다. project/revision/scope는 snapshot에서, 문서의 specId/path/kind/title/sourceHash는 documents에서 읽어 이중 기록하지 않는다. 기존 traceability_json v1의 저장 형식과 API-027/028은 유지한다.

항목 해시는 확정 prd-requirements와 tasks의 실제 AST H2 정의 구간을 대상으로 한다. 정의 제목 시작부터 다음 H1/H2 직전 또는 문서 끝까지의 원문을 LF로 통일해 UTF-8 SHA-256을 만든다. 코드·HTML도 원문 변화로서 해시에 포함한다. 코드 안의 가짜 제목은 AST 정의가 아니므로 구간을 나누지 않는다. 줄 끝 공백·서식 차이도 변경이다. 의미적 동등성을 주장하지 않는다.

제목·본문을 포함한 sectionHash가 달라지면 원문 변경이다. 메타데이터만 바뀌면 문서 해시 변화로 표시하고, 확정에서 검토로 바뀌어 항목 대상에서 빠지면 ‘확정 정의에서 제외’로 설명한다. H1/서론처럼 항목 밖의 변화는 문서 변경으로 남기고 특정 REQ 변화로 추정하지 않는다.

중복 ID·누락/잘못된 작성 상태·해시/구간을 만들 수 없는 정의는 partial 진단을 남긴다. 알려진 항목은 표시하되, 부분 인덱스에서 항목이 없다는 이유로 추가/제외를 확정하지 않는다. 분석기·직렬화 예외는 수집 실패이며 현재 게시본 전환 전에 중단한다. 임대·원자적 게시·마지막 정상 게시본 유지 원칙을 보존한다.

### 비교 규약

문서는 활성 규칙의 DOC-NNN 형식을 만족하는 양쪽의 유일한 specId로 맞춘다. 잘못된 ID는 진단을 남기고 안정된 ID로 사용하지 않는다. 양쪽 모두 ID가 없을 때만 같은 정확한 경로로 맞추며 이 경우 파일 이동을 판정하지 않는다. 같은 경로에서 DOC ID가 바뀌거나 한쪽만 ID가 생긴 경우는 추가/제외 행과 identity_changed 진단을 함께 보여주고 동일 문서라고 추정하지 않는다. 경로만 같은 다른 DOC를 합치지 않는다.

항목은 같은 프로젝트·kind·itemId로 맞춘다. 양쪽에서 유일해야 한다. 항목의 documentId UUID로 맞추지 않는다. 문서를 옮기거나 항목을 다른 문서로 옮겨도 ID가 유지되면 위치 변화로 표시한다. 양쪽에서 중복된 항목은 임의 대응시키지 않고 unknown이다.

변경 값은 added/removed/modified/moved/moved_modified/unchanged/unknown이다. 위치와 해시는 독립적으로 비교한다. 문서 원문 해시는 renderer 변경으로 생긴 HTML 차이와 구분한다. 항목 hash algorithm이 다르면 unknown이며 unchanged로 표시하지 않는다. 불완전 인덱스의 미존재 항목도 unknown이다.

두 게시본의 범위 필드가 알려져 있고 branch/docsRoot가 같아야 한다. 서로 다르면 409 COMPARISON_SCOPE_MISMATCH로 선택을 바로잡게 한다. NULL 비교 자료/범위/지원하지 않는 보고서 버전이면 200 unchecked와 reason, counts=null, 빈 결과를 제공한다. 빈 결과를 ‘변경 없음’으로 표시하지 않는다. 같은 유효 게시본끼리는 전부 unchanged이고 재검토 후보가 없다. 정상적으로 수집한 빈 문서 집합은 자료 부족과 별도다.

### 변경 영향 규약

added/modified/removed/moved_modified REQ에 대해 이전·현재 traceability의 TASK→REQ 관계를 합쳐 역참조한다. 이동만 있고 원문이 같으면 위치 변화 안내와 현재 참조 진단을 보여주며 내용 변경 대상으로 세지 않는다. TASK 원문 자체가 달라졌다는 사실과 REQ 변화로 재검토 후보가 됐다는 사실을 별도 표시한다.

후보에는 이유가 된 요구 ID·변경 종류·이전/현재 원문 위치·현재 관찰한 TaskView를 제공한다. 없어진 TASK는 이전 원문으로 연결하고 현재 실행 상태를 추정하지 않는다. 순수 문서/서식 변화로 관련 요구를 특정할 수 없으면 문서 변경만 표시한다. 관계의 추가/제거 자체를 새 영향 분석 범위로 늘리지 않는다.

양쪽 관계 분석이 complete일 때 알려진 영향 범위를 complete로 표시한다. partial이면 알려진 후보만 표시하고 coverage=incomplete를 명시한다. unchecked/해석 불가이면 unknown이다. 양쪽 인덱스가 완전하고 양쪽 모두 확정 요구가 없는 경우에만 not_applicable로 구분한다. 한쪽 요구가 없어졌더라도 이전 관계의 알려진 후보는 보존하며, 반대편 관계가 unchecked이면 coverage는 unknown으로 남긴다. 후보 0을 ‘영향 없음’이나 ‘구현 정상’의 보증으로 쓰지 않는다. Issue/PR 변화는 원문 변경이나 비교 결과를 바꾸지 않는다.

