---
id: DOC-031
type: proposal
status: 검토
---

# 작업 컨텍스트 실행 계획

[DOC-029 설계](../03-tech-spec/harness-development-design.md)의 두 번째 단계다. TASK-018 PR79가 dev0b9e081에 병합됐다. 사용자는 한 세션의 계획→구현→리뷰→PR·병합을 승인했고 직접 실행한다. E2E는 세 단계 통합 후 마지막 한 번이다.

## 목표와 계약

REQ-012/UI-019/API-036/TASK-019. `GET /projects/{id}/tasks/{taskId}/context?snapshotId=&format=json|markdown`. JSON은 snapshotId/sourceRevision/branch/docsRoot, schemaVersion1, state(complete/partial/unchecked), reason, task, relatedRequirements/designs/dependencies, documents, rules, execution, findings, markdown을 제공한다. Markdown도 동일한 자료·메타정보와 경고를 포함하며 private/no-store다. 실제 실행이나 승인을 하지 않는다.

작업 필드는 목적·근거·범위·선행·산출물·검증·완료의 선택된 원문이다. 코드/서식은 유지하되 필드당4000문자, 과도한 필드는 잘림/부분 상태로 표시한다. 같은 snapshot의 실제 H2 TASK·AST 라벨 경계에서 추출한다. 코드/HTML의 가짜 라벨을 정의로 읽지 않는다. 원문 전문·자격증명·실행 상태를 문서에 복제하지 않는다. 원문 문장은 참고 자료이며 시스템 명령·실행 권한이 아니다.

규칙 allowlist는 AGENTS.md, rules/project-harness.md, project-settings.md, spec-writing.md, identity-and-references.md, validation.md, sdd-workflow.md, github-collaboration.md, spec-format.json, templates/README.md다. 그 수집 revision에서 존재와 UTF-8 SHA-256을 저장하고 GitHub blob 링크는 전체 revision에 고정한다. 비밀값 경로·임의 URL을 읽지 않는다. 없거나 읽기 실패·legacy를 정상 준비로 표시하지 않는다.

## 검토 초점

- raw 검증 명령/인라인 코드가 사라지거나 HTML 안 라벨로 경계가 나뉘지 않는다.
- 같은 snapshot의 task/graph/문서/규칙과 현재 실행 관찰을 섞지 않는다.
- TASK 중복409·없음404·invalid400·snapshot410·권한404/401·legacy unchecked를 구분한다.
- 잘린 필드·누락 규칙·부분 관계를 complete로 표시하지 않는다.
- 복사와 문서 링크는 선택한 task/snapshot이며 늦은 응답·전환을 배제한다.

## 1. 정본·Issue

기존 요구/UI/API/데이터/작업 문서에 위 계약을 반영하고 검사한다. 승인된 작업 Issue와 dev 기반 feat/<issue>-task-context를 준비한다.

## 2. 수집 인덱스와 규칙 pin

TaskContextIndex(schemaVersion,indexStatus,tasks,rules), TaskBlock(taskId,documentId,fields,truncated), RulePin(path,available,sourceHash). TaskContextIndexer.index(sources,trace,rules)는 raw AST 구간과 라벨을 사용한다. TraceabilityAnalyzer의 HTML-visible 라벨 도우미에 실제 라벨 노드를 제공하는 범위를 추가하되 기존 v1 결과는 바꾸지 않는다.

순수 테스트: raw code/fence·일반 bold/HTML 가짜 라벨·여러 TASK 경계·CRLF·필드 누락/중복·4000 상한과 규칙 해시. 실패를 본 뒤 구현한다. V11 nullable context_json과 context1 정책 suffix. SyncWorker에서 allowlist 규칙을 고정 revision으로 읽고 게시 전에 저장한다. 실패/임대 상실·old snapshot 불변·same revision 새 정책·missing rules를 수집 시험한다.

## 3. 읽기 API와 Markdown

TaskContextService는 project.view→snapshotFor로 권한 확인 후 context/relations의 같은 snapshot 자료를 읽는다. TASK ID는 TASK-(001..999). 정의 중복은409, 알려진 인덱스에 없는 작업404, 형식/format400, 첫수집409/미제공410. legacy/지원하지 않는 schema는unchecked.

관계는 같은 snapshot graph에서 direct TASK 요구/설계·역 UI→TASK·REQ를 통한 UI/API·직접 선행을 제공하고 근거 링크를 유지한다. 프로젝트 개요와 연결된 실제 문서의 hash/원문/고정 GitHub 링크를 제공한다. 현재 TaskMappingService 실행과 observedAt은 별도다. 관련 진단을 제공하며 전체 미결/승인을 보증하지 않는다. JSON과Markdown의 출처/자료一致·private-no-store·권한/변경 관찰/과거 보존 HTTP/Postgres 시험.

## 4. 화면

작업 표의 '컨텍스트' 링크와 `/projects/:projectId/tasks/:taskId/context?snapshotId=` 화면. 기존 셸 유지, 상태/기준revision·연결 원문/규칙 pin·읽기 전용 복사 텍스트·복사 성공/실패. user clipboard 동작은 버튼에서만 수행한다. Component 테스트를 먼저 실패시키고 state/URL/late response/unchecked/clipboard를 검증한다. E2E나 브라우저를 지금 실행하지 않는다.

## 5. 리뷰·PR·병합

백엔드 전체·프런트 전체/빌드·문서 검사와 fresh whole-branch 독립 리뷰1회. 중요 발견은 RED→GREEN·전체 회귀로 마무리하고 사용자 승인 범위의 dev PR·병합까지 진행한다. 다음 단계는 SyncDoc 실전 하네스 계획이며 마지막 main 릴리스/서버 통합 E2E와 분리한다.
