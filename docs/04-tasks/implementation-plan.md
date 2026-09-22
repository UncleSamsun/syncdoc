---
id: DOC-014
type: tasks
status: 확정
---

# SyncDoc MVP 구현 계획

사용자가 현재 계획으로 확정하고 커밋·푸시 후 Claude에서 UI 작업을 이어가기로 했다. UI 인수인계는 [시작 안내](../02-ui-spec/ui-handoff.md)를 따른다. 아래 기본값은 첫 구현 기준으로 채택하며 실계정 연결·버전 호환성은 여전히 실제 검증이 필요하다.

### 목표

초대 사용자가 GitHub 저장소를 연결하고 현황·문서·표·다이어그램을 권한 범위에서 읽는다.

### 구조

React/TypeScript 웹, Spring Boot4/Java25 API와 worker, PostgreSQL. commonmark-java가 본문·표를 변환하고 Mermaid가 브라우저에서 다이어그램을 렌더한다.

### 설계

[프로젝트 개요](../01-prd/overview.md), [기능·인수 기준](../01-prd/mvp-scope.md), [API](../03-tech-spec/api-spec.md), [데이터](../03-tech-spec/data-model.md). 요구는 `REQ-NNN`, 화면은 `UI-NNN`, 계약은 `API-NNN`으로 가리킨다. 실행 시 이 문서와 연결 설계를 함께 읽는다. 실행 방식은 순차 작업을 기본으로 하며 승인된 계획을 executing-plans 절차로 수행한다. 작업 상태·담당자는 GitHub에서 관리하고 이 문서에 체크 상태를 복제하지 않는다.

### 실행 순서 (2026-09-10 사용자 확정)

TASK-009 ~ TASK-012는 2026-09-22에 더했다. TASK-011·TASK-012는 전체 점검에서 찾은 것으로, 명세가 이미 정해 둔 조작이 구현에 없던 자리다.

TASK-009·TASK-010은 TASK-001 ~ TASK-008을 마치며 남겨 둔 판단 둘을 사용자가 확정한 것이고, 새 요구가 아니라 이미 있는 계약·규칙의 빈자리를 메운다.

TASK-004를 다음 작업으로 올린다. UI 명세가 확정되었고 기준의 정본 위치를 [REQ-008](../01-prd/mvp-scope.md)로 확정했으므로, 문서 규약을 활성 규칙으로 세우는 일이 나머지 구현보다 앞선다. 규약이 없는 상태로 수집·렌더·집계를 만들면 무엇을 검사할지가 구현 중에 암묵적으로 정해진다.

## 공통 제약과 파일 배치

React + TypeScript, Spring Boot 4 + Java 25, PostgreSQL, commonmark-java/GFM 표 확장, Mermaid. 확정된 협업·참조 규칙은 rules를 따른다. 웹 편집·공개 가입·서비스 내부 Issue 발행 자동화는 제외한다. API·worker는 같은 코드베이스를 profile로 분리한다. 기본 세부 선택은 Gradle Kotlin DSL, Spring Security, Spring Data JPA, Flyway, Vitest/React Testing Library, 브라우저 E2E로 제안한다. 작업 큐 잠금은 SQL로 구현한다. 실제 dependency 버전은 TASK-001에서 공식 호환성 확인 후 lock/wrapper에 고정한다.

| 경로 | 책임 |
|---|---|
| backend/build.gradle.kts, settings.gradle.kts, gradlew* | Java25 빌드와 테스트 |
| backend/src/main/java/io/github/unclesamsun/syncdoc/{auth,project,github,sync,document,dashboard} | 기능별 API·서비스·저장소 |
| backend/src/main/resources/db/migration/ | 스키마 버전 관리 |
| backend/src/test/java/io/github/unclesamsun/syncdoc/ | 기능·DB·권한 테스트 |
| frontend/src/{app,features,shared}/ | 라우팅·기능 UI·API 공통 |
| frontend/tests/ | 단위 및 실제 브라우저 흐름 |
| rules/spec-writing.md, rules/validation.md | 작성·검증 규칙 |
| tools/spec-validator/ | 필수 내용·ID·일반 링크 검사 |
| deploy/compose.yaml, deploy/.env.example | 클라우드/사내 공통 실행 및 설정 안내 |

아래 파일은 생성 예정이며 현재 존재하거나 실행 가능하다고 주장하지 않는다. 각 작업은 검증 재현 실패 → 최소 구현 → 통과 확인 → diff 검토 → 승인된 GitHub 작업으로 PR 준비 순서를 따른다. 일반 명령은 backend에서 `./gradlew test`, frontend에서 `npm run test -- --run`·`npm run build`; 실제 스크립트 정의는 첫 작업에 포함한다.

## TASK-001 실행 기반과 연결 가능성 확인

**근거:** REQ-001, REQ-002.

**선행:** 없다. 첫 작업이다.

**산출물:** 위 backend 빌드, frontend/package.json·src/app, deploy/compose.yaml, backend의 auth·GitHub gateway와 테스트 fake를 만든다. 결과물은 로컬 실행 골격, 실제 실행·검증 명령, 잠긴 dependency 버전, GitHub 지원 매트릭스다. 자격증명이 없으면 로컬 골격은 진행할 수 있지만 실제 로그인·Project 통합을 준비 완료로 표시하지 않는다.

**검증:** Java25/Boot4 부팅·PostgreSQL 연결과 API-023 응답 테스트를 작성한다. GitHub 인증 포트를 fake로 두고 미인증 API-003이 401을 돌려주는 것을 재현한다. 실제 승인된 App·테스트 계정을 쓸 수 있을 때 사용자 접근·설치 접근·개인/조직 Project 조회·scope 부족을 read-only 연결 실험으로 확인한다. 외부 App 등록은 설정과 필요한 권한을 구체화한 뒤 수행한다.

**완료:** Java 25와 PostgreSQL로 부팅하고 API-023이 응답하며 미인증 API-003이 401을 돌려준다. 실제 자격증명이 없는 동안 GitHub 통합 준비 완료로 표시하지 않는다.

## TASK-002 초대와 세션

**근거:** REQ-001, REQ-007.

**선행:** TASK-001.

**산출물:** auth/{AuthController,InvitationController,SessionService,GitHubIdentityGateway}, migration V1__identity.sql, auth/AccessBoundaryTest, frontend/features/auth/. 구현하는 계약은 API-001 ~ API-007이다.

**검증:** 미초대 거절, `state` 재사용·만료 거절, 초대 취소 후 기존 쿠키 거절, 계정명 변경 유지 테스트를 먼저 작성하고 구현한다. OAuth 토큰은 암호화 저장하고 세션 회전·CSRF를 검증한다.

**완료:** 초대한 계정만 진입하며 로그아웃·초대 회수 효과를 검증한다. 관리자 권한은 GitHub 저장소 열람 권한을 대체하지 않는다.

## TASK-003 저장소 연결과 목록

**근거:** REQ-002, REQ-007.

**선행:** TASK-002.

**산출물:** project/{ProjectController,ProjectService}, github/RepositoryAccessGateway, migration V2__projects.sql, project/ProjectConnectionTest, frontend/features/projects/. 구현하는 계약은 API-008 ~ API-012다.

**검증:** 저장소 중복 동시 요청, 사용자만·앱만 접근 가능한 경우, 잘못된 `branch`·`docsRoot`, 타인이 연결한 프로젝트의 수정 거절 테스트를 작성한다. 서버가 GitHub ID에서 저장소를 해소하고 경로·권한을 검증하게 구현한다.

**완료:** 관리 가능한 저장소를 연결하고 첫 동기화 대기를 목록에 표시한다. 다른 사용자 데이터로 권한 우회 불가.

## TASK-004 문서 규약과 검증 — 다음 작업

**근거:** REQ-008.

**선행:** 규칙 파일과 독립 실행 검증기(`tools/spec-validator/`)는 선행이 없다. Java 실행 기반 없이 저장소 문서만으로 검사할 수 있다. `backend/document/SpecMetadataParser`와 산출물 체크리스트 API 구현만 TASK-001을 선행으로 둔다. 이 분리 덕에 TASK-001 이전에 착수할 수 있다.

**산출물:** rules/spec-format.json·spec-writing.md·validation.md, tools/spec-validator/, backend/document/SpecMetadataParser, 문서 fixture, 산출물 체크리스트의 계약·화면 명세.

**검증:** 검사 항목마다 정상 fixture와 실패 fixture를 두고, 오류가 재현되는 테스트를 먼저 만들어 통과시킨다. 같은 규칙을 두 개 샘플 프로젝트에 적용해 오류 파일·항목·위치가 제시되는지 확인한다. 규칙 파일이 없는 프로젝트에서 미검사로 보고되는지 확인한다.

### 순서

1. `spec-standard-proposal.md` §2의 산출물 목록과 유형별 필수 내용을 `rules/spec-writing.md`로 승격한다. 적용 조건, 적용하지 않는 유형의 사유 기록 방식, 최소 메타데이터 필드, ID 발급·유지 규칙을 확정한다. 승격 후 제안 문서에는 링크만 남기고 중복 본문을 두지 않는다.
2. `rules/validation.md`에 검사 항목·실행 시점·실패 처리를 확정한다. 검사 대상은 필수 내용 누락, 중복 ID, 깨진 파일 링크, 없는 앵커, 코드 블록 안 가짜 링크, 이동 파일의 상대 이미지 경로다.
3. 정상 문서와 위 오류별 fixture를 만들고 오류가 재현되는 테스트를 먼저 작성한다.
4. `tools/spec-validator/`를 구현한다. 오류 파일·항목·위치를 제시한다.
5. 적용 규칙 버전을 [프로젝트 설정](../../rules/project-settings.md) 한 곳에서 읽도록 연결한다.
6. TASK-001 이후: `SpecMetadataParser`, 산출물 체크리스트 조회 계약을 [API 계약](../03-tech-spec/api-spec.md)의 계약 일람에 새 `API-NNN`으로, 화면을 [화면 명세](../02-ui-spec/ui-screens.md)에 추가한다. 기준을 고치는 화면은 만들지 않는다.

**완료:** 같은 규칙을 두 개 샘플 프로젝트에 적용하고 오류 파일·항목을 정확히 제시한다. 규약이 구현 중 암묵적으로 달라지지 않게 한다. 규칙 파일이 없는 프로젝트를 통과로 표시하지 않는다.

## TASK-005 수집·스냅샷·재시도

**근거:** REQ-006.

**선행:** TASK-003, TASK-004.

**산출물:** sync/{SyncWorker,SyncJobRepository,GitHubWebhookController}, migration V3__sync_and_snapshots.sql, sync/SyncRecoveryTest. 구현하는 계약은 API-013, API-014, API-021이다.

**검증:** 중복 delivery, 수집 중 새 push, 두 worker 동시 claim, lease 만료 뒤 오래된 worker 완료, GitHub 429·5xx·페이지 중간 실패·삭제된 문서 fixture를 테스트한다. source revision 고정 수집, 원자적 게시, 임대 token과 backoff를 구현한다.

**완료:** 재시작 후 재개하며 실패/부분 갱신이 기존 정상 snapshot을 덮어쓰지 않는다. 진단 오류에 토큰이 포함되지 않는다.

## TASK-006 문서·표·다이어그램

**근거:** REQ-004, REQ-005, REQ-007.

**선행:** TASK-004, TASK-005.

**산출물:** document/{DocumentController,MarkdownRenderService,HtmlPolicy,AssetService}, document/DocumentRenderingTest, frontend/features/documents/{DocumentPage,DiagramView}와 관련 테스트. 구현하는 계약은 API-017, API-018, API-020이다.

**검증:** 표·한글 제목·명시 앵커·상대 링크·접기 fixture와 script·event attribute·위험 URL fixture를 만든다. commonmark-java 표 확장과 HTML 정화를 구현한다. Mermaid 원문은 별도 응답으로 보내고 React에서 strict 설정·크기 제한·격리된 렌더 실패 처리를 구현한다.

**완료:** 다섯 Mermaid 유형, 잘못된 문법, 시간 제한·너무 큰 입력, 권한 없는 이미지/과거 snapshot 직접 요청을 검증한다. 문서 작성 시점의 원문이 임의로 실행되지 않는다.

## TASK-007 현황·검색·작업 매핑

**근거:** REQ-003, REQ-004, REQ-007.

**선행:** TASK-003, TASK-005, TASK-006.

**산출물:** dashboard/{OverviewController,TaskMappingService,ProgressCalculator}, document/SearchService, dashboard/VisibilityAndProgressTest, frontend/features/dashboard/. 구현하는 계약은 API-015, API-016, API-019다.

**검증:** TASK와 Issue 일대일·중복 주장, 미등록 작업, 취소·재개, 여러 PR, 분모 0, Project 권한 없음, 숨겨진 다른 저장소 항목, 불완전 pagination을 테스트한다. 사용자별 Project 조회와 범위가 명시된 집계를 구현한다.

**완료:** 프로젝트 선택 후 현황·문서 목록이 보이고 문서를 바로 열 수 있다. 커밋 수나 PR 수를 작업 완료율로 사용하지 않는다. 검색에서 다른 프로젝트 데이터가 나오지 않는다.

## TASK-008 실제 흐름·호스팅 검증

**근거:** REQ-001 ~ REQ-008 전체.

**선행:** TASK-001 ~ TASK-007.

**산출물:** frontend/tests/mvp.spec.ts, deploy Dockerfiles·compose·운영 안내, 필요한 CI workflow.

**검증:** 서로 다른 GitHub 접근 권한의 초대 계정 2개와 미초대 1개를 준비하고, 실험 저장소에서 연결 → 수집 → 문서·표·다이어그램 → Issue 상태 변경 → 현황 갱신 → 권한 회수 흐름을 확인한다. DB·worker 재시작, GitHub 중단, 백업 복원, 원문 파서 공격 fixture를 검증한다. 타인 자료를 사용하지 않는다.

**완료:** 컨테이너 구성으로 재현 가능하고 실제 검증 결과·남은 제한을 기록한다. 외부 서버를 선택하지 않았다면 로컬 컨테이너 검증으로 명확히 구분한다. 운영 배포·main 릴리스는 담당자 판단 이후다.

## TASK-009 작업 전체 목록 화면

**근거:** REQ-003. 전체 목록 계약 API-016은 TASK-007에서 만들었으나 부르는 화면이 없다. 현황 화면의 작업 표는 첫 20건이고, [화면 명세](../02-ui-spec/ui-screens.md)의 경로 표와 작업 표 규칙이 이미 "작업 화면에서 이어 본다"고 적어 둔 채 그 화면이 비어 있다.

**선행:** TASK-007.

**산출물:** 화면 명세에 UI-014, 경로 표와 화면·계약 표의 행, frontend/features/dashboard의 목록 화면, 셸 이동 항목 `작업`의 연결 대상 변경.

**검증:** 작업이 20건을 넘는 게시본에서 전부를 볼 수 있다. 상태·담당 거르기가 계약대로 동작한다. 취소된 작업이 완료로 세어지지 않고 Issue 미등록 작업이 목록에서 사라지지 않는다. 현황 표의 `<표시건수>건 중 <전체>건`에서 이어 갈 곳이 실제로 있다.

**완료:** 사이드바 `작업`이 그 화면으로 가고, 20건 넘는 프로젝트에서 나머지를 볼 방법이 생긴다. 계약을 새로 만들지 않는다.

## TASK-010 문서 경로 밖 링크

**근거:** REQ-004·REQ-005. 문서가 수집 대상(`docs/`) 밖의 파일을 가리키면 지금은 `LINK_TARGET_NOT_FOUND` 경고와 함께 빈 링크(`href=""`)로 남는다. 링크처럼 보이는데 누르면 현재 페이지가 새로 고쳐진다. 2026-09-22 이 저장소 게시본에서 51건이며 39건이 `rules/`다 — 문서가 기준으로 가장 자주 참조하는 파일이다.

**선행:** TASK-006.

**산출물:** [API 계약](../03-tech-spec/api-spec.md)의 링크 변환 규칙(지금은 서비스가 만든 경로만 허용한다), `document/MarkdownRenderService`의 링크 해소, 화면의 표시.

**검증:** 저장소 안이지만 수집 대상 밖인 경로가 수집 시점 revision에 고정된 GitHub 주소로 열린다. 저장소 밖으로 나가는 경로(`LINK_OUTSIDE_REPOSITORY`)는 여전히 열지 않는다. 어떤 경우에도 `href=""`가 남지 않는다. 비공개 저장소에서는 GitHub 권한이 없는 사람에게 열리지 않으며 그것을 서비스가 감추지 않는다.

**완료:** 이 저장소 게시본의 끊긴 링크가 실제로 열린다. 열 수 없는 링크는 링크로 보이지 않는다.

## TASK-011 공통 셸의 빠진 조작

**근거:** REQ-001·REQ-002·REQ-006. [화면 명세](../02-ui-spec/ui-screens.md) UI-000이 정한 상단바 조작과 갱신 규칙이 구현에 없다. 2026-09-22 전체 점검에서 확인했다. 상단바에서 누를 수 있는 것이 제품 표식 하나뿐이고, 로그아웃할 방법이 없으며, UI-006의 `로그아웃` 버튼은 `/login`으로 이동만 하고 세션을 지우지 않는다(누른 뒤 `GET /me`가 200이다). 브랜치 전환 절 전체와 20초 주기 갱신·새 버전 배너도 없다. 경로 표의 `/projects/:projectId/documents`는 프로젝트 홈으로 떨어진다.

**선행:** TASK-006. 계약은 API-004·API-012·API-014·API-024로 모두 있다. 새 계약을 만들지 않는다.

**산출물:** frontend/features/shell의 상단바 조작(계정 표식과 로그아웃, 브랜치 스위처), 20초 주기 갱신과 새 버전 배너, `/projects/:projectId/documents` 경로, UI-006의 로그아웃 동작 수정.

**검증:** 로그아웃을 누르면 세션이 실제로 무효화되고 로그인 화면으로 간다 — 그 뒤 계약 호출이 401이다. 미초대 화면의 로그아웃도 같다. 브랜치 스위처는 읽을 수 있는 브랜치만 나열하고 직접 입력을 받지 않으며, 바꾸면 `expectedVersion`과 CSRF를 함께 보내고 새 수집이 끝날 때까지 이전 게시본을 보여준다. 권한이 없으면 목록을 열지 않는다. 새 게시본이 게시되면 현황·목록은 조용히 갱신되고 문서 본문에서는 배너만 뜬다 — 읽던 위치를 잃지 않는다.

**완료:** UI-000의 사용자 행동 일곱 가지가 화면에서 실제로 가능하다. 문서 목록 경로가 UI-003의 목록 상태를 연다.

## TASK-012 연결 설정 화면

**근거:** REQ-002. 경로 표에 `/projects/:projectId/settings`가 있는데 그 화면의 정의가 명세에 없고 구현도 없다. 문서 경로와 GitHub Project 연결을 바꿀 수단이 화면에 없어 API를 직접 부르지 않으면 고칠 수 없다.

**선행:** TASK-011. 상단바의 연결 설정 진입점이 먼저 있어야 한다.

**산출물:** 화면 명세에 UI-015, 경로 표와 화면·계약 표의 행, frontend의 연결 설정 화면.

**검증:** 연결자 또는 서비스 관리자만 바꿀 수 있고 그 외에는 읽기 전용이다. 바꾸면 `expectedVersion`과 CSRF를 보내고 버전 충돌(409)에 정한 문구를 쓴다. 문서 경로를 바꾸면 새 수집이 돌고, 끝날 때까지 이전 게시본을 보여준다. 저장소 연결 자체를 이 화면에서 끊지 않는다 — 그 계약이 없다.

**완료:** 문서 경로와 GitHub Project 연결을 화면에서 바꿀 수 있고, 권한이 없는 사람에게는 바꾸는 수단이 보이지 않는다.

## 실행 준비 판정

이 설계와 작업계획은 사용자가 확인했다. 실제 GitHub App/조직 Project 접근 확인은 TASK-001의 우선 검증 사항이다. 초대 방식·관리 단위·권한 재확인·정량 제한은 첫 구현 기준으로 채택한다. 실제 외부 인증이 없는 상태에서 통합 검증 완료를 주장하지 않는다. Claude는 UI를 API 계약에 맞춘 fixture로 먼저 구현할 수 있으며 서버 의존 작업의 완료를 대신하지 않는다. 구현 작업의 Issue는 해당 작업 착수 시 등록한다.
