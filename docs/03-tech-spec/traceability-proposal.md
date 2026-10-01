---
id: DOC-025
type: proposal
status: 검토
---

# 요구–작업 추적성 설계 검토

사용자 요청: 2026-10-01 SDD 레퍼런스 조사 이후 다음 구현으로 진행할 수 있도록 준비한다. 첫 범위는 요구와 작업의 연결 표 및 잘못된 참조·미연결 진단이다. 이 문서는 구체적인 설계 제안이며 확정 명세를 대체하지 않는다. 실행 순서는 [작업계획 (DOC-026)](../04-tasks/traceability-plan.md)에 있다.

2026-10-01 사용자 확정: 첫 단계 구현을 진행한다. 활성 구현 기준은 REQ-009·UI-016·API-027/028·TASK-016과 데이터 설계로 승격했다. 아래는 제안의 검토 근거다.

## 목적과 범위

[개요 (DOC-016)](../01-prd/overview.md)의 명세·GitHub 실행 상태 연결 목표를 확장한다. 요구를 선택하면 그 요구를 근거로 삼는 작업과 원문·Issue·PR을 찾고, 연결되지 않거나 해소할 수 없는 관계를 확인한다.

첫 구현은 확정한 `prd-requirements` 문서의 REQ와 확정한 `tasks` 문서의 TASK만 대상으로 한다. UI/API 추적, 선행 순환 검사, 게시본 비교, 변경 영향, CI 증거·승인 자동 판정, MCP·에이전트 실행, 외부 포맷 어댑터는 후속 범위다. 기존 TASK–Issue 충돌·미등록 검사를 재구현하지 않는다.

### 인수 조건

| 조건 | 확인할 동작 |
|---|---|
| 연결 | 요구 행에서 이를 근거로 삼는 TASK·원문 위치와 현재 관찰한 Issue·PR로 이동한다 |
| 미연결 | 전 범위 분석이 가능한데 참조하는 TASK가 없는 요구는 `작업 미연결`로 표시한다. 요구 미구현을 뜻하지 않는다 |
| 잘못된 참조 | TASK 근거가 없는 REQ ID를 가리키면 ID와 원문 위치를 표시한다 |
| 중복 | REQ/TASK ID가 같은 프로젝트·게시본 안에서 중복이면 아무 항목도 임의 선택하지 않는다 |
| 부분 분석 | 해석하지 못한 근거가 있으면 `부분 분석`으로 표시하고 미연결 여부는 `판정 보류`로 표시한다 |
| 시간 기준 | 관계는 snapshot/sourceRevision에 고정하고 Issue·PR은 관찰 시점의 값이라는 점을 표시한다 |
| 접근 | 프로젝트·snapshot 열람 판정을 거친다. 다른 프로젝트나 회수된 게시본의 자료가 섞이지 않는다 |
| 기존 게시본 | 분석 결과가 없는 게시본은 `미분석`으로 표시한다. 결과 없음이나 통과로 표시하지 않는다 |

## 접근 대안과 선택

1. **수집 중 분석 결과를 snapshot에 저장 — 추천.** 수집 중 이미 읽는 Markdown과 렌더러의 실제 앵커를 활용한다. 동일 게시본은 같은 관계 결과를 제공하고 조회 시 GitHub 원문을 다시 요청하지 않는다.
2. 조회 때 원문 재수집·분석. 저장 필드는 줄지만 revision 일관성·요청 제한·실패 처리가 복잡해진다.
3. 관계 전용 정규화 테이블. 대규모 검색에는 유리하나 첫 범위에서는 엔티티·이관 비용이 크다. 관계 표가 실제로 사용된 후 검토한다.

추천안은 불변 JSON 보고서 하나를 snapshot에 추가하고 조회 시 기존 TaskMappingService의 최신 관찰 결과를 합성한다. 현재 비어 있는 TaskEntity의 참조 필드를 또 다른 정본으로 채우지 않는다. 원문의 근거가 정본이고 JSON은 revision별 파생 결과다.

## 분석 입력과 규약

### 입력

동일 revision에서 성공적으로 수집한 문서의 documentId, 저장소 경로, 원문 Markdown, frontmatter, 렌더러 headings를 입력으로 받는다. 원문은 분석 후 메모리에서 버리고 기존 저장 정책을 바꾸지 않는다. 코드 블록·인라인 코드·HTML·예시 문서의 ID는 관계로 세지 않는다.

확정 `prd-requirements`에서 `## REQ-NNN 제목`, 확정 `tasks`에서 `## TASK-NNN 제목`을 정의로 읽는다. 초안·검토·폐기 및 proposal/guide/record는 정의와 관계 추출에서 제외한다. 문서 ID는 DOC, 항목 ID는 REQ/TASK로 별도 취급한다. 문서 자체의 기존 C0/C1/C2 검사 결과는 바꾸지 않는다.

### 관계 추출

TASK 섹션의 `**근거:**` 문단에서 일반 텍스트의 REQ-NNN, REQ ID를 담은 링크 글자, 목록과 같은 접두어의 범위를 읽는다. `REQ-001 ~ REQ-008`은 양끝 포함, 오름차순, 동일 접두어, 세 자리 ID일 때만 펼친다. REQ-000·역순·접두어 혼합·네 자리 ID는 유효 관계로 만들지 않고 `UNSUPPORTED_REFERENCE`로 보고한다. 코드 안 ID는 무시한다. REQ ID가 전혀 없는 근거는 `TASK_WITHOUT_REQUIREMENT` 정보로 보고한다. 상위 요구 관계가 없는 문서 작업도 있으므로 오류로 단정하지 않는다.

관계의 방향은 TASK → REQ이다. `(projectId, snapshotId, itemId)`로 이름 공간을 구분한다. 근거에 같은 ID가 여러 번 있으면 관계는 하나로 합치고 최초 근거 위치를 남긴다. 링크에 경로가 있으면 정규화한 대상 문서가 같은 snapshot에서 해당 REQ 정의를 갖는지도 확인한다. 경로 밖 이동·외부 URL은 링크를 요청하지 않는다. GitHub URL은 첫 범위에서 ID 관계만 해소하고 URL 목적지는 검증하지 않는다.

중복 REQ/TASK는 `DUPLICATE_ITEM_ID`, 없는 REQ는 `MISSING_REQUIREMENT`, 로컬 링크 경로와 ID가 다른 문서에 대응하면 `REFERENCE_TARGET_MISMATCH`다. 링크 fragment 도달성은 첫 범위의 검사 대상이 아니다. 이동 링크에는 렌더러가 생성한 실제 heading id를 사용한다.

### 진단과 상태

보고서 상태는 `complete`(전 범위 분석), `partial`(해석 불가 입력 포함), `unchecked`(분석 전)다. 상태는 요구 충족이나 승인 여부를 뜻하지 않는다. 진단은 code/severity/documentId/path/line/itemId/targetId/message를 갖고 원문 전체는 포함하지 않는다. 동일 code·위치·대상은 중복 제거한다.

중복 ID, 깨진 구조·메타데이터, 해석 불가 참조로 전체 관계를 신뢰할 수 없으면 partial이다. 근거 라벨이 없거나 비어 있어도 partial이며 `TASK_EVIDENCE_UNREADABLE`로 표시한다. 부분 분석에서는 연결이 확인된 요구만 `linked`로 표시하고 나머지는 `unknown`으로 둔다. complete에서만 관계가 없는 요구를 `unlinked`로 판정한다. 없는 ID와 명백한 경로 불일치는 완전하게 확인한 오류이므로 그 자체로 partial을 만들지는 않는다.

확정 요구 문서가 없으면 보고서는 `unchecked`, 이유는 `NO_CONFIRMED_REQUIREMENTS`다. 확정 작업 문서가 없으면 `unchecked`/`NO_CONFIRMED_TASKS`로 표시하고 요구는 unknown이다. 기존 체크리스트의 필수 문서 누락과 별도다. REQ/TASK 대상 종류를 선언한 문서의 status가 빠졌거나 허용 값이 아니면 의도적 초안 제외로 처리하지 않고 partial 진단을 남긴다.

## 저장과 게시

다음 migration 후보는 현재 V7 다음의 `V8__spec_traceability.sql`이다. 착수 시 다시 확인한다. document_snapshots에 nullable `traceability_json`을 추가한다. NULL은 기존 게시본의 미분석이고, 새 보고서는 schemaVersion=1을 포함한다.

보고서는 requirements, tasks, edges, findings, analysisStatus, uncheckedReason를 담는다. 항목은 itemId/title/documentId/path/anchor/line, 관계는 taskId/requirementId/sourceLocation을 갖는다. projectId/snapshotId/sourceRevision은 snapshot 정본에서 읽고 JSON에 중복 저장하지 않는다.

관계 계산은 모든 원문을 읽은 뒤, 현재 checklist 저장과 snapshot 완료·원자적 게시 전 수행한다. 문서의 참조 오류는 진단 결과로 게시할 수 있지만 분석기 예외·직렬화 실패는 수집 실패로 처리해 마지막 정상 게시본을 유지한다. 회수·삭제 시 snapshot과 함께 지워지며 별도 정리가 필요하지 않다.

분석 버전이 바뀌면 DocumentVersions의 기존 정책 버전을 올려 같은 commit도 새 분석 게시본을 만들도록 한다. 기존 게시본을 조회하면서 소급 수정하지 않는다. 현재 최대 문서 수·파일 크기 제한을 유지한다.

## 조회 계약 후보

두 GET 계약을 제안한다. API ID는 확정 API 계약에 승격할 때 사용 중인 최대 번호 다음에서 발급한다.

| 경로 (기존 /api/v1 아래) | 입력 | 출력 |
|---|---|---|
| `/projects/{id}/spec-traceability` | 선택 snapshotId, page=0, size=50(1~100), coverage=all/linked/unlinked/unknown | snapshotId/sourceRevision/analysisVersion/analysisStatus/uncheckedReason, requirements[]와 연결 tasks[] 및 기존 TaskView, totalElements/page/size |
| `/projects/{id}/spec-traceability/findings` | 선택 snapshotId, page=0, size=50(1~100) | 같은 snapshot 기준 메타데이터와 findings[], totalElements/page/size |

REQ ID 오름차순으로 요구 행을 정렬한다. 진단은 path/line/code/targetId 순으로 정렬한다. analysisVersion은 저장한 schemaVersion이고 기존 NULL 보고서는 null이다. 응답의 task 상태는 TaskMappingService가 계산하고 기존 Issue 관찰 시각을 보존한다. 문서 부분은 불변이어도 실행 상태는 변하므로 응답은 private/no-store다. 사용자 권한을 건너뛰는 프로젝트별 응답 캐시를 두지 않는다.

프로젝트 없음/열람 불가·다른 프로젝트 snapshot·미완성 snapshot은 기존 snapshotFor의 판정과 동일하게 처리한다. 첫 수집 전 409, 회수된 게시본 410, 인증 없는 요청은 기존 인증 정책, 잘못된 pagination/filter는 400이다. 기존 게시본 미분석은 200/unchecked이지 서버 오류가 아니다. 실제 구현은 기존 컨트롤러와 ApiError 형태를 따른다.

## 화면 후보

기존 산출물 체크리스트 화면에 `요구와 작업` 구획을 추가한다. 별도 메뉴·새 그래프 화면을 만들지 않는다. 요구/연결 작업/Issue 상태/원문 표와 연결 상태 필터, 페이지 이동, 진단 목록을 제공한다. 문서 링크에는 응답 snapshotId와 실제 anchor를 전달한다.

분석 상태와 sourceRevision을 함께 보여주고 실행 상태의 관찰 시점은 별도로 표시한다. 분석 오류는 기존 C0/C1/C2 산출물 오류 건수에 합산하지 않는다. 부분 분석·미분석·요구 없음·작업 없음·통신 실패·권한 없음·회수된 게시본을 구분한다. 게시본 갱신 시 두 API는 같은 snapshotId로 요청하고 오래된 응답이 새 프로젝트·게시본을 덮어쓰지 않게 한다.

## 확정과 착수 조건

사용자가 승인하면 이 범위를 개요·MVP 요구, 화면 명세, API 계약, 데이터 모델, 구현계획, 필요한 검증 규칙에 승격한다. REQ/UI/API/TASK 번호는 착수 시 중복 확인 후 발급한다. 이 제안의 관계 진단은 기존 C0/C1/C2 확대가 아니라 별도 분석이므로 검사 범위를 조용히 바꾸지 않는다.

그 후 승인된 작업계획의 Issue를 기존 TASK 접두사 규약으로 등록하고 dev에서 작업 브랜치를 준비한다. 현재 작업 중인 작성 하네스·화면 변경과 공통 파일(AppShell/ChecklistPage/SyncWorker)을 비교해 관련 변경이 섞이지 않게 한다. 본 문서만으로 기존 변경을 커밋·버리거나 에이전트가 병합할 권한이 생기지 않는다.
