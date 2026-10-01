---
id: DOC-024
type: guide
status: 확정
---

# 문서와 산출물 진입 안내

대상은 SyncDoc을 사용하는 사람과 문서를 작성하는 에이전트다. 먼저 [프로젝트 개요 (DOC-016)](01-prd/overview.md)와 [작성 하네스](../rules/project-harness.md)를 읽는다. 폴더의 모든 문서를 현재 명세로 간주하지 않는다.

## 현재 구현 기준을 찾는 순서

| 영역 | 산출물 종류 | 현재 기준 |
|---|---|---|
| 무엇을 왜 만드는가 | 프로젝트 개요 | [개요 (DOC-016)](01-prd/overview.md) |
| 어떤 동작·품질이 필요한가 | 기능·품질 요구 | [기능·품질 요구 (DOC-002)](01-prd/mvp-scope.md) |
| 공통 화면 표현 | 공통 UI 규칙 | [UI 규칙 (DOC-005)](02-ui-spec/ui-conventions.md) |
| 화면에서 무엇을 하는가 | 화면 명세 | [화면 명세 (DOC-006)](02-ui-spec/ui-screens.md) |
| 경계와 모듈 책임 | 기술 개요 | [구현 구조 (DOC-009)](03-tech-spec/architecture-proposal.md) |
| 경계를 오가는 계약 | 인터페이스 계약 | [API 계약 (DOC-010)](03-tech-spec/api-spec.md) |
| 저장하는 정보와 수명 | 데이터 설계 | [데이터 모델 (DOC-011)](03-tech-spec/data-model.md) |
| 배포·복구·관측 | 실행·운영 | [운영 (DOC-020)](03-tech-spec/ops.md), [회사 서버 CI/CD (DOC-022)](03-tech-spec/cicd.md) |
| 무엇을 어떻게 구현·검증하는가 | 작업 정의 | [구현계획 (DOC-014)](04-tasks/implementation-plan.md), [배포 계획 (DOC-023)](04-tasks/cicd-plan.md) |

기계 정본은 [포맷 정의](../rules/spec-format.json)이며 이 표는 SyncDoc 프로젝트의 실제 문서로 가는 안내다. 종류별 필수 내용은 [작성 규칙](../rules/spec-writing.md), 적용 여부·버전은 [프로젝트 설정](../rules/project-settings.md)에서 읽는다.

## 다음 구현 준비

2026-10-01 사용자가 REQ–TASK 관계·요구별 연결 표·참조 진단을 첫 후속 범위로 확정했다. [TASK-016](https://github.com/UncleSamsun/syncdoc/issues/70)의 별도 기능 브랜치에서 정본 승격·구현·검증을 진행한다. 검토 자료의 경로와 활성 구현 기준은 해당 기능 PR에서 함께 제공한다.

## 기준과 자료를 구분하기

`status: 확정`인 명세를 구현 기준으로 사용한다. 검토 초안·안내·경과 기록은 판단 근거와 출처이며 구현 기준을 대신하지 않는다. 파일명에 남은 `proposal`만으로 현재 상태를 추정하지 않는다. 기존 경로/ID를 유지해 링크가 깨지지 않게 하고, 새 프로젝트에서는 [템플릿](../templates/README.md)을 시작점으로 새 ID를 발급한다.

SyncDoc 화면은 원문의 폴더 계층으로 탐색한다. 폴더 묶음과 문서 목록을 구분해 표시하고 탐색 방식 전환 버튼은 두지 않는다. 산출물 옆의 오류 건수와 문서 개수는 서로 다른 값이다.

## 문서 이름을 정하는 기준

명세는 문서 종류를 H1에 쓰고 여러 문서가 필요하면 `종류 — 주제`로 구분한다. 폴더는 영역을, 제목은 산출물 역할을, frontmatter는 ID·종류·작성 상태를 나타낸다. 원문 폴더 계층 하나로 탐색하며 제목과 작성 형식은 [작성 규칙](../rules/spec-writing.md)의 정본을 따른다.
