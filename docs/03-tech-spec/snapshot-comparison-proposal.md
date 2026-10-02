---
id: DOC-027
type: proposal
status: 검토
---

# 게시본 비교와 변경 영향 설계 검토

2026-10-02 사용자가 완료 상태 안내 정리 이후 게시본 비교·변경 영향 설계를 진행하도록 요청했다. 목적은 두 게시본 사이에 무엇이 달라졌고 어느 작업을 다시 검토할지 찾는 것이다. 이 문서는 설계 검토 자료이며 현재 REQ-009·API-027/028·TASK-016의 확정 계약을 변경하지 않는다. 제품 코드·DB·서버는 이번 준비에서 변경하지 않는다.

## 첫 범위

같은 프로젝트·같은 수집 브랜치·같은 문서 루트의 완료 게시본 두 개를 선택한다. 문서의 추가·제외·위치·원문 변경과 확정 REQ/TASK 항목의 추가·제외·위치·원문 변경을 표시한다. 변경된 요구를 참조하는 TASK를 재검토 후보로 보여주고, 양쪽 게시본의 실제 원문 앵커와 현재 관찰한 Issue/PR로 이동한다.

‘제외’는 해당 게시본/확정 정의 집합에 없다는 뜻이다. Git 삭제나 구현 실패를 뜻하지 않는다. 변경은 원문 차이이며 의미적 오류 판정이 아니다. 관계가 있다는 사실도 요구 충족 증명이 아니다.

UI/API 관계 확장·선행 순환·코드 변경 영향·CI/승인 자동 판정·AI 의미 분석·에이전트 실행·웹 편집·GitHub 쓰기는 제외한다. 비교 화면은 열람 기능이며 기존 C0/C1/C2와 완료율을 바꾸지 않는다.

## 현재 코드에서 확인한 제약

- `documents.source_hash`는 SyncWorker가 원문 UTF-8 바이트의 SHA-256으로 저장한다. 문서의 DB UUID는 게시본마다 새로 발급된다. 안정된 문서 식별은 `spec_id`이고 ID 없는 문서도 읽을 수 있다.
- traceability_json/schemaVersion=1에는 항목 ID·제목·문서 UUID·경로·앵커·줄·관계가 있지만 항목 본문 해시는 없다. 제목만 비교하면 본문만 바뀐 요구를 놓친다.
- document_snapshots는 branch/docsRoot를 저장하지 않는다. 현재 프로젝트 설정을 과거 게시본의 설정으로 추정하면 안 된다.
- 현재 재사용 키 `document_snapshots_identity_key`와 repository 조회는 project/revision/renderer/policy만 본다. 같은 commit의 다른 문서 루트를 수집할 때도 구분해야 한다.
- 완료 게시본과 관계 보고서는 불변이다. 원문 Markdown 전문은 DB에 추가로 저장하지 않는다. 비교 조회에서 GitHub 원문을 재수집하거나 기존 게시본을 소급 갱신하지 않는다.

## 대안과 제안

| 방식 | 이점 | 제약 |
|---|---|---|
| 기존 문서 해시만 비교 | 저장 변경이 작음 | 요구 본문 변경을 특정하지 못하고 문서 전체의 작업을 검토 후보로 과다 표시함 |
| 새 수집에서 항목 해시까지 저장 — 제안 | 본문만 바뀐 REQ/TASK도 정확히 특정, 동일 비교의 재현 가능 | 저장/재사용 키 변경과 새 비교 자료가 필요, 기존 자료 부족은 미확인 처리 |
| 조회 때 GitHub 원문 재수집 | 과거 자료 보완 가능 | 네트워크·권한·요청 제한·실패와 파서 버전 재현 문제가 늘어남 |

두 번째를 제안한다. 비교 자료가 없는 과거 게시본은 미확인으로 표시한다. 최초 배포 직후 비교 가능한 같은 범위의 새 게시본이 두 개 필요하다. 이 제약을 숨기려고 과거 scope나 본문 해시를 추측하지 않는다.

## 수집과 저장 제안

V8 다음 migration은 착수 때 번호를 재확인한다. nullable `collection_branch`, `collection_docs_root`, `comparison_json`을 snapshot에 추가한다. NULL은 기존 게시본의 자료 부족이다. 새 수집은 실제로 사용한 branch/docsRoot를 처음부터 고정한다.

`document_snapshots_identity_key`는 projectId/revision/renderer/policy/collectionBranch/collectionDocsRoot로 확장하고 repository 조회도 같은 값을 쓴다. 기존 project/currentSnapshot 복합 FK는 유지한다. policy 버전을 올려 새 수집부터 scope·비교 자료를 채운다. 이전 완료 게시본은 그대로 둔다. 새 필드가 한쪽만 비거나 잘못되면 미확인이지 같은 범위로 간주하지 않는다.

comparison_json은 schemaVersion=1, fingerprintAlgorithm=`markdown-section-lf-v1`, indexStatus=complete/partial, 문서의 작성 상태, 항목 목록과 진단을 담는다. 항목은 kind=req/task, itemId, documentId, title, anchor, line, sectionHash다. project/revision/scope는 snapshot에서, 문서의 specId/path/kind/title/sourceHash는 documents에서 읽어 이중 기록하지 않는다. 기존 traceability_json v1의 저장 형식과 API-027/028은 유지한다.

항목 해시는 확정 prd-requirements와 tasks의 실제 AST H2 정의 구간을 대상으로 한다. 정의 제목 시작부터 다음 H1/H2 직전 또는 문서 끝까지의 원문을 LF로 통일해 UTF-8 SHA-256을 만든다. 코드·HTML도 원문 변화로서 해시에 포함한다. 코드 안의 가짜 제목은 AST 정의가 아니므로 구간을 나누지 않는다. 줄 끝 공백·서식 차이도 변경이다. 의미적 동등성을 주장하지 않는다.

제목·본문을 포함한 sectionHash가 달라지면 원문 변경이다. 메타데이터만 바뀌면 문서 해시 변화로 표시하고, 확정에서 검토로 바뀌어 항목 대상에서 빠지면 ‘확정 정의에서 제외’로 설명한다. H1/서론처럼 항목 밖의 변화는 문서 변경으로 남기고 특정 REQ 변화로 추정하지 않는다.

중복 ID·누락/잘못된 작성 상태·해시/구간을 만들 수 없는 정의는 partial 진단을 남긴다. 알려진 항목은 표시하되, 부분 인덱스에서 항목이 없다는 이유로 추가/제외를 확정하지 않는다. 분석기·직렬화 예외는 수집 실패이며 현재 게시본 전환 전에 중단한다. 임대·원자적 게시·마지막 정상 게시본 유지 원칙을 보존한다.

## 비교 규약

문서는 활성 규칙의 DOC-NNN 형식을 만족하는 양쪽의 유일한 specId로 맞춘다. 잘못된 ID는 진단을 남기고 안정된 ID로 사용하지 않는다. 양쪽 모두 ID가 없을 때만 같은 정확한 경로로 맞추며 이 경우 파일 이동을 판정하지 않는다. 같은 경로에서 DOC ID가 바뀌거나 한쪽만 ID가 생긴 경우는 추가/제외 행과 identity_changed 진단을 함께 보여주고 동일 문서라고 추정하지 않는다. 경로만 같은 다른 DOC를 합치지 않는다.

항목은 같은 프로젝트·kind·itemId로 맞춘다. 양쪽에서 유일해야 한다. 항목의 documentId UUID로 맞추지 않는다. 문서를 옮기거나 항목을 다른 문서로 옮겨도 ID가 유지되면 위치 변화로 표시한다. 양쪽에서 중복된 항목은 임의 대응시키지 않고 unknown이다.

변경 값은 added/removed/modified/moved/moved_modified/unchanged/unknown이다. 위치와 해시는 독립적으로 비교한다. 문서 원문 해시는 renderer 변경으로 생긴 HTML 차이와 구분한다. 항목 hash algorithm이 다르면 unknown이며 unchanged로 표시하지 않는다. 불완전 인덱스의 미존재 항목도 unknown이다.

두 게시본의 범위 필드가 알려져 있고 branch/docsRoot가 같아야 한다. 서로 다르면 409 COMPARISON_SCOPE_MISMATCH로 선택을 바로잡게 한다. NULL 비교 자료/범위/지원하지 않는 보고서 버전이면 200 unchecked와 reason, counts=null, 빈 결과를 제공한다. 빈 결과를 ‘변경 없음’으로 표시하지 않는다. 같은 유효 게시본끼리는 전부 unchanged이고 재검토 후보가 없다. 정상적으로 수집한 빈 문서 집합은 자료 부족과 별도다.

## 변경 영향 규약

added/modified/removed/moved_modified REQ에 대해 이전·현재 traceability의 TASK→REQ 관계를 합쳐 역참조한다. 이동만 있고 원문이 같으면 위치 변화 안내와 현재 참조 진단을 보여주며 내용 변경 대상으로 세지 않는다. TASK 원문 자체가 달라졌다는 사실과 REQ 변화로 재검토 후보가 됐다는 사실을 별도 표시한다.

후보에는 이유가 된 요구 ID·변경 종류·이전/현재 원문 위치·현재 관찰한 TaskView를 제공한다. 없어진 TASK는 이전 원문으로 연결하고 현재 실행 상태를 추정하지 않는다. 순수 문서/서식 변화로 관련 요구를 특정할 수 없으면 문서 변경만 표시한다. 관계의 추가/제거 자체를 새 영향 분석 범위로 늘리지 않는다.

양쪽 관계 분석이 complete일 때 알려진 영향 범위를 complete로 표시한다. partial이면 알려진 후보만 표시하고 coverage=incomplete를 명시한다. unchecked/해석 불가이면 unknown이다. 양쪽 인덱스가 완전하고 양쪽 모두 확정 요구가 없는 경우에만 not_applicable로 구분한다. 한쪽 요구가 없어졌더라도 이전 관계의 알려진 후보는 보존하며, 반대편 관계가 unchecked이면 coverage는 unknown으로 남긴다. 후보 0을 ‘영향 없음’이나 ‘구현 정상’의 보증으로 쓰지 않는다. Issue/PR 변화는 원문 변경이나 비교 결과를 바꾸지 않는다.

## 조회 계약 후보

API ID는 정본 승격 시 실제 마지막 번호 다음에서 발급한다. 모두 기존 `/api/v1/projects/{id}` 아래 GET이며 매 요청 project.view와 각 snapshotFor를 통해 권한·소속·완료·보관 판정을 거친다. 프로젝트 404, 지정 게시본 없음/외부 프로젝트/미완성 410은 기존 계약과 같게 둔다. 인증 없는 요청은 기존 인증 정책이다. 응답은 private/no-store이며 권한 검증을 생략하는 캐시는 두지 않는다.

| 경로 | 입력 | 출력 |
|---|---|---|
| `/snapshots` | page=0,size=20(1~100) | 완료 게시본 ID/revision/생성시각/renderer/policy/scope/current/comparisonReadiness, 페이지 정보 |
| `/snapshot-comparison` | 필수 fromSnapshotId,toSnapshotId | 양쪽 기준, status/reason, nullable 종류별 counts, 관계 coverage, 진단 요약 |
| `/snapshot-comparison/documents` | 두 ID,page=0,size=50(1~100),change=all 또는 변경 값 | 문서 변경 행·양쪽 원문 위치·진단, 페이지 정보 |
| `/snapshot-comparison/items` | 두 ID,page/size,kind=all/req/task,change | 항목 변경 행·양쪽 원문 위치·진단, 페이지 정보 |
| `/snapshot-comparison/impacts` | 두 ID,page/size | REQ별 재검토 TASK 후보·근거·현재 TaskView, 페이지 정보 |

음수 page·size 범위 초과·잘못된 필터·누락 ID는400이다. 문서/항목은 안정된 식별 키·경로·항목 ID 순, 후보는REQ ID·TASK ID 순으로 정렬한다. 큰 page 곱셈은 long으로 계산한다. 모든 응답에 from/to ID·revision을 포함한다. 원문은 기존 문서 API와 실제 anchor/snapshotId로 이동한다. 별도 원문 diff 전문을 새 응답에 복제하지 않는다.

## 화면 제안

산출물 화면 안에 ‘게시본 비교’ 진입점을 둔다. 첫 범위는 비교 대상 선택, 변경 요약, 문서/항목/재검토 후보 탭이다. 별도 route 후보는 `/projects/:projectId/comparison?fromSnapshotId=...&toSnapshotId=...`다. 메뉴를 추가하거나 그래프를 만들지 않는다.

최신 완료 게시본과 같은 scope의 직전 비교 가능한 게시본을 기본 후보로 고르고 없으면 선택 필요 상태를 표시한다. 생성시각을 Git ancestry 순서로 표현하지 않는다. from은 기준, to는 대상이며 순서를 바꾸면 added/removed 방향도 바뀐다. 각 탭 요청은 같은 두 ID로 고정하고 변경 시 이전 요청을 취소해 늦은 응답이 덮어쓰지 못하게 한다.

미확인 자료, 다른 범위, 부분 비교, 관계 coverage 미완료, 회수410, 첫 게시본 대기, 권한404, 통신 실패, 정상 변경0을 구분한다. 현재 Issue 관찰시각은 revision과 분리한다. 넓은 표는 내부에서 스크롤하고 sidebar 드래그·고정 상단바·키보드 조작을 유지한다.

## 인수 검토 목록

본문만 수정/제목만 수정/CRLF와서식/항목 밖 수정, DOC 유지 이동/동일경로 ID 교체/무ID 이동, REQ/TASK 추가·제외/작성상태 변경/중복과 partial, 같은commit 다른문서루트의 별도수집, 기존NULL과 버전차이, Scope불일치, 과거보고서 불변, 임대상실/분석실패, 프로젝트권한/410/400/pagination, Issue만변경, 늦은 응답·원문 링크를 각각 재현한다. 실제 서버·GitHub 저장소 시험과 모킹 시험은 구분한다.

## 다음 승인과 실행 계획

이 문서 확인 후 요구·UI·API·데이터·작업 정본에 승인 범위를 분배하고 실행 계획을 작성한다. 계획은 fingerprint/scope 저장·순수 비교/영향 계산·권한 조회·화면·실제 검증의 순서로 나누고 구현 ID·Issue는 그때 중복 확인 후 발급한다. 새 기능이 이미 구현되었거나 배포되었다고 기록하지 않는다.
