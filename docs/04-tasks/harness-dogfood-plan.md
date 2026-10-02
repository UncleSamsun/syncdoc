---
id: DOC-032
type: proposal
status: 검토
---

# SyncDoc 하네스 실전 적용 계획

[DOC-029](../03-tech-spec/harness-development-design.md)의 마지막 단계다. 사용자 선택에 따라 대상은 SyncDoc 자체다. 앞선 PR79/81이 dev6b0fc7b에 병합됐다. 계획→구현→리뷰→PR·병합 후 전체 dev를 main으로 배포하고 E2E 한 번을 수행한다. REQ-013/TASK-020, 새 제품 API/UI는 추가하지 않는다.

## 목표와 산출물

기존 작성 하네스·규칙·템플릿·C0/C1/C2를 그대로 적용한다. 읽기 전용 `tools/harness/dogfood.py <root> --task TASK-NNN [--context-json FILE]`와 실전 가이드 DOC-033을 추가한다. 설치 CLI·규칙 자동 복제/업데이트·Git/문서 쓰기·명령 실행은 제외한다.

도구는 AGENTS.md와 rules/project-harness.md의 실제 로컬 링크에서 필요한 rules/*.md/json 및 templates/README.md를 찾는다. 새 규칙 목록을 수동으로 복제하지 않는다. 진입점 docs/README.md·프로젝트 개요, 정본의 적용 Spec/문서 검사 결과·Git HEAD/변경 상태를 보고한다. TASK의 오프라인 위치는 후보이며 실제 정체성/관계는 서버 AST 컨텍스트가 정본이다. 코드/HTML 예시 안 제목을 후보로 세지 않는다.

기존 Python 검사기를 모듈로 불러 C0/C1/C2를 수행하고 통과·미검사·오류를 분리한다. 검사 범위를 확대하지 않는다. Source 경로가 root 밖으로 벗어나거나 symlink로 탈출하면 읽기 전에 거절한다. subprocess는 Git 읽기만, shell=False, 실제 사용자 명령/검증 문구는 실행하지 않는다.

컨텍스트 JSON을 주면 TASK·schema/state·sourceRevision이 Git HEAD와 맞고 tracked 변경이 없는지 확인한다. 규칙 경로 집합과 실제 commit blob의 SHA-256을 대조한다. Windows CRLF 작업 파일과 Git blob bytes를 혼동하지 않는다. legacy/partial은 상태를 유지하며 unknown을 verified로 표시하지 않는다. report는 JSON stdout, 성공0/오류1이고 대상 파일을 바꾸지 않는다. credential 값/쿠키/원문 전문은 출력하지 않는다.

## 검토 초점

- 검사 대상0·없는 규칙·미검사를 pass로 합치지 않는다.
- 고정 sourceRevision과 dirty 작업 트리/CRLF를 혼동하지 않는다.
- context mismatch/누락·중복/임의 경로/변조 해시는 검증 성공이 아니다.
- symlink·경로 탈출과 문서 안 명령의 실행은 없다.
- 실제 SyncDoc에서 작업을 찾고 검사·서버 컨텍스트 pin을 재현할 수 있다.

## 1. 정본·Issue·테스트

REQ013/TASK020과 DOC032·가이드 연결을 작성하고 검사한다. 승인된 Issue와 dev 기반 작업 브랜치를 준비한다. Python 표준 라이브러리 unittest와 임시 Git fixture로 정상·규칙/진입점 누락·미검사·TASK 후보/fence/HTML·경로/symlink·context revision/dirty/hash·파일 불변을 먼저 실패시킨다.

## 2. 읽기 도구·가이드

check(root,taskId,context?)는 entrypoints/rulePins/taskCandidate/validation/source/contextVerification/findings를 반환한다. validator.validate(root)가 권위이며 stdout/source를 임의 통과로 바꾸지 않는다. 규칙 pins는 Git HEAD blob과 actual allowlist 링크로 생성한다. JSON 없는 오프라인 검사는 local-only로 표시한다.

DOC033은 작업 컨텍스트→규칙→정본 작성/수정→C0/C1/C2→PR 검토→수집→관계/컨텍스트 재확인 순서, 원문·실행 관찰·검사·승인 구분, CLI/API 사용과 pin/legacy/partial 실패를 설명한다. rules/project-harness.md·문서 진입점에 연결하고 기존 템플릿 정의는 유지한다. CI의 문서 job에 도구 단위 시험을 추가한다.

## 3. 실제 적용·리뷰·dev 병합

SyncDoc 실제 작업 TASK020을 도구로 찾고 C0/C1/C2와 진입점/규칙을 확인한다. target 파일 hash 전후를 대조한다. 단위·문서 검증, 필요한 제품 회귀와 독립 whole-branch 리뷰1회. 중요 발견은 RED→GREEN, PR/CI와 dev 병합까지 수행한다. 오프라인 확인을 서버/E2E 완료라고 표시하지 않는다.

## 4. 마지막 통합 배포·E2E

세 작업이 병합된 dev revision 전체를 main으로 merge PR·CI 후 배포한다. DB 사전 백업, Actions GHCR digest/promotion·Flux exact revision·Ready/health/V10·V11을 확인한다. 같은 actor의 격리 테스트 브랜치에서 관계·설계 영향·컨텍스트·규칙 pin 자료를 수집하고 기존+신규 E2E를 한 suite로 한 번 실행한다. 실제 main TASK020의 API 컨텍스트를 저장해 CLI commit/hash 검증을 대조한다. 실패 재시험이 필요하면 조건과 결과를 분리한다. main/docs 복귀 후 vault에 최종 근거를 기록한다.


## 통합 검증 후 보정

최종 서버 통합 시험에서 Windows 클립보드 CRLF와 현황 API의 제한된 작업 배열을 잘못 기대한 시험 두 곳을 확인했다. 복사 내용은 줄바꿈 표현만 정규화하고, 현황 행은 실제 API 배열과 전체 건수를 각각 비교한다. 최초 전체 시험과 실패 두 곳 재시험 결과를 구분해 기록한다.

같은 실제 수집에서 TASK-017의 API 범위가 `API-029~033`으로 축약돼 관계 분석이 partial로 남았다. 기존 계약 범위를 바꾸지 않고 접두사를 양쪽에 적는 규칙대로 보정한다. 이 후속은 테스트·문서만 변경하며 코드 동작을 바꾸지 않는다. 문서 검사·빌드·두 실패 시험 재검증과 diff 리뷰 후 dev PR/병합, main 반영을 진행한다. 최종 서버의 main/docs 재수집에서 관계 진단과 TASK-020 규칙 pin을 다시 확인한다. 이미 통과한 제품 E2E를 반복하지 않는다.
