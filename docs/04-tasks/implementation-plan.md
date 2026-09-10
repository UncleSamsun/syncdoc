---
id: DOC-014
type: tasks
status: 확정
---

# SyncDoc MVP 구현 계획

사용자가 현재 계획으로 확정하고 커밋·푸시 후 Claude에서 UI 작업을 이어가기로 했다. UI 인수인계는 [시작 안내](../02-ui-spec/ui-handoff.md)를 따른다. 아래 기본값은 첫 구현 기준으로 채택하며 실계정 연결·버전 호환성은 여전히 실제 검증이 필요하다.

**목표:** 초대 사용자가 GitHub 저장소를 연결하고 현황·문서·표·다이어그램을 권한 범위에서 읽는다.

**구조:** React/TypeScript 웹, Spring Boot4/Java25 API와 worker, PostgreSQL. commonmark-java가 본문·표를 변환하고 Mermaid가 브라우저에서 다이어그램을 렌더한다.

**설계:** [기능·인수 기준](../01-prd/mvp-scope.md), [API](../03-tech-spec/api-spec.md), [데이터](../03-tech-spec/data-model.md). 실행 시 이 문서와 연결 설계를 함께 읽는다. 실행 방식은 순차 작업을 기본으로 하며 승인된 계획을 executing-plans 절차로 수행한다. 작업 상태·담당자는 GitHub에서 관리하고 이 문서에 체크 상태를 복제하지 않는다.

**실행 순서 (2026-09-10 사용자 확정).** TASK-004를 다음 작업으로 올린다. UI 명세가 확정되었고 기준의 정본 위치를 [REQ-008](../01-prd/mvp-scope.md)로 확정했으므로, 문서 규약을 활성 규칙으로 세우는 일이 나머지 구현보다 앞선다. 규약이 없는 상태로 수집·렌더·집계를 만들면 무엇을 검사할지가 구현 중에 암묵적으로 정해진다.

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

**근거:** REQ-001/002. 선행 없음. 생성: 위 backend 빌드, frontend/package.json·src/app, deploy/compose.yaml, backend의 auth/GitHub gateway와 테스트 fake.

검증 순서: Java25/Boot4 부팅·PostgreSQL 연결과 /health/ready 응답 테스트를 작성한다. GitHub 인증 포트를 fake로 두고 미인증 /me=401을 재현한다. 실제 승인된 App/테스트 계정을 사용할 수 있을 때 사용자 접근·설치 접근·개인/조직 Project 조회·scope 부족을 read-only 연결 실험으로 확인한다. 외부 App 등록은 설정과 필요한 권한을 구체화한 후 수행한다.

산출물: 로컬 실행 골격, 실제 실행/검증 명령, 잠긴 버전, GitHub 지원 매트릭스. 자격증명이 없으면 로컬 골격은 진행 가능하지만 실제 로그인/Project 통합 준비 완료로 표시하지 않는다.

**완료:** Java 25와 PostgreSQL로 부팅하고 `/health/ready`가 응답하며 미인증 `/me`가 401을 돌려준다. 실제 자격증명이 없는 동안 GitHub 통합 준비 완료로 표시하지 않는다.

## TASK-002 초대와 세션

**근거:** REQ-001/007. 선행 TASK-001. 생성: auth/{AuthController,InvitationController,SessionService,GitHubIdentityGateway}, migration V1__identity.sql, auth/AccessBoundaryTest, frontend/features/auth/.

API: auth/start·callback, me, logout, invitations. 순서: 미초대 거절, state 재사용/만료 거절, 초대 취소 후 기존 쿠키 거절, 계정명 변경 유지 테스트를 작성하고 구현한다. OAuth 토큰은 암호화 저장하고 세션 회전/CSRF를 검증한다.

**완료:** 초대한 계정만 진입하며 로그아웃·초대 회수 효과를 검증한다. 관리자 권한은 GitHub 저장소 열람 권한을 대체하지 않는다.

## TASK-003 저장소 연결과 목록

**근거:** REQ-002/007. 선행 TASK-002. 생성: project/{ProjectController,ProjectService}, github/RepositoryAccessGateway, migration V2__projects.sql, project/ProjectConnectionTest, frontend/features/projects/.

API: github/repositories, projects 목록/등록/조회/수정. 순서: 저장소 중복 동시 요청, 사용자만/앱만 접근 가능한 경우, 잘못된 branch/docsRoot, 타인 연결 수정 거절 테스트를 작성한다. 서버가 GitHub ID에서 저장소를 해소하고 경로·권한을 검증하게 구현한다.

**완료:** 관리 가능한 저장소를 연결하고 첫 동기화 대기를 목록에 표시한다. 다른 사용자 데이터로 권한 우회 불가.

## TASK-004 문서 규약과 검증 — 다음 작업

**근거:** REQ-008. 생성: rules/spec-writing.md·validation.md, tools/spec-validator/, backend/document/SpecMetadataParser, 문서 fixture, 산출물 체크리스트의 계약·화면 명세.

**선행 분리.** 규칙 파일과 독립 실행 검증기(`tools/spec-validator/`)는 TASK-001을 선행으로 두지 않는다. Java 실행 기반 없이 저장소 문서만으로 검사할 수 있다. `backend/document/SpecMetadataParser`와 산출물 체크리스트 API 구현만 TASK-001을 선행으로 둔다. 이 분리 덕에 TASK-001 이전에 착수할 수 있다.

순서:

1. `spec-standard-proposal.md` §2의 산출물 목록과 유형별 필수 내용을 `rules/spec-writing.md`로 승격한다. 적용 조건, 적용하지 않는 유형의 사유 기록 방식, 최소 메타데이터 필드, ID 발급·유지 규칙을 확정한다. 승격 후 제안 문서에는 링크만 남기고 중복 본문을 두지 않는다.
2. `rules/validation.md`에 검사 항목·실행 시점·실패 처리를 확정한다. 검사 대상은 필수 내용 누락, 중복 ID, 깨진 파일 링크, 없는 앵커, 코드 블록 안 가짜 링크, 이동 파일의 상대 이미지 경로다.
3. 정상 문서와 위 오류별 fixture를 만들고 오류가 재현되는 테스트를 먼저 작성한다.
4. `tools/spec-validator/`를 구현한다. 오류 파일·항목·위치를 제시한다.
5. 적용 규칙 버전을 [프로젝트 설정](../../rules/project-settings.md) 한 곳에서 읽도록 연결한다.
6. TASK-001 이후: `SpecMetadataParser`, 산출물 체크리스트 조회 계약을 [API 계약](../03-tech-spec/api-spec.md)에, 화면을 [화면 명세](../02-ui-spec/ui-screens.md)에 추가한다. 기준을 고치는 화면은 만들지 않는다.

**완료:** 같은 규칙을 두 개 샘플 프로젝트에 적용하고 오류 파일·항목을 정확히 제시한다. 규약이 구현 중 암묵적으로 달라지지 않게 한다. 규칙 파일이 없는 프로젝트를 통과로 표시하지 않는다.

## TASK-005 수집·스냅샷·재시도

**근거:** REQ-006. 선행 TASK-003/004. 생성: sync/{SyncWorker,SyncJobRepository,GitHubWebhookController}, migration V3__sync_and_snapshots.sql, sync/SyncRecoveryTest.

API: sync 요청/상태, webhook. 순서: 중복 delivery, 수집 중 새 push, 두 worker 동시 claim, lease 만료 뒤 오래된 worker 완료, GitHub429/5xx·페이지 중간 실패·삭제된 문서 fixture를 테스트한다. source revision 고정 수집, 원자적 게시, 임대 token과 backoff를 구현한다.

**완료:** 재시작 후 재개하며 실패/부분 갱신이 기존 정상 snapshot을 덮어쓰지 않는다. 진단 오류에 토큰이 포함되지 않는다.

## TASK-006 문서·표·다이어그램

**근거:** REQ-004/005/007. 선행 TASK-004/005. 생성: document/{DocumentController,MarkdownRenderService,HtmlPolicy,AssetService}, document/DocumentRenderingTest, frontend/features/documents/{DocumentPage,DiagramView}, 관련 테스트.

API: documents·document detail·assets. 순서: 표/한글/명시 앵커/상대 링크/접기 fixture와 script·event attribute·위험 URL fixture를 만든다. commonmark-java 표 확장과 HTML 정화를 구현한다. Mermaid 원문은 별도 응답으로 보내고 React에서 strict 설정·크기 제한·격리된 렌더 실패 처리를 구현한다.

**완료:** 다섯 Mermaid 유형, 잘못된 문법, 시간 제한·너무 큰 입력, 권한 없는 이미지/과거 snapshot 직접 요청을 검증한다. 문서 작성 시점의 원문이 임의로 실행되지 않는다.

## TASK-007 현황·검색·작업 매핑

**근거:** REQ-003/004/007. 선행 TASK-003/005/006. 생성: dashboard/{OverviewController,TaskMappingService,ProgressCalculator}, document/SearchService, dashboard/VisibilityAndProgressTest, frontend/features/dashboard/.

API: overview·tasks·search. 순서: TASK와 Issue 일대일/중복 주장, 미등록 작업, 취소/재개, 여러 PR, 분모0, Project 권한 없음, 숨겨진 다른 저장소 항목, 불완전 pagination을 테스트한다. 사용자별 Project 조회와 범위가 명시된 집계를 구현한다.

**완료:** 프로젝트 선택 후 현황·문서 목록이 보이고 문서를 바로 열 수 있다. 커밋 수나 PR 수를 작업 완료율로 사용하지 않는다. 검색에서 다른 프로젝트 데이터가 나오지 않는다.

## TASK-008 실제 흐름·호스팅 검증

**근거:** 전체 REQ. 선행 TASK-001~007. 생성: frontend/tests/mvp.spec.ts, deploy Dockerfiles/compose/운영 안내, 필요한 CI workflow.

순서: 서로 다른 GitHub 접근 권한의 초대 계정2개와 미초대1개를 준비하고 실험 저장소에서 연결→수집→문서/표/다이어그램→Issue 상태 변경→현황 갱신→권한 회수 흐름을 확인한다. DB/worker 재시작, GitHub 중단, 백업 복원, 원문 파서 공격 fixture를 검증한다. 타인 자료를 사용하지 않는다.

**완료:** 컨테이너 구성으로 재현 가능하고 실제 검증 결과·남은 제한을 기록한다. 외부 서버를 선택하지 않았다면 로컬 컨테이너 검증으로 명확히 구분한다. 운영 배포·main 릴리스는 담당자 판단 이후다.

## 실행 준비 판정

이 설계와 작업계획은 사용자가 확인했다. 실제 GitHub App/조직 Project 접근 확인은 TASK-001의 우선 검증 사항이다. 초대 방식·관리 단위·권한 재확인·정량 제한은 첫 구현 기준으로 채택한다. 실제 외부 인증이 없는 상태에서 통합 검증 완료를 주장하지 않는다. Claude는 UI를 API 계약에 맞춘 fixture로 먼저 구현할 수 있으며 서버 의존 작업의 완료를 대신하지 않는다. 구현 작업의 Issue는 해당 작업 착수 시 등록한다.
