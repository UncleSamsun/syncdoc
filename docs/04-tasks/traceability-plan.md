---
id: DOC-026
type: proposal
status: 검토
---

# 요구–작업 추적성 실행 계획 검토

설계 정본: [추적성 설계 제안 (DOC-025)](../03-tech-spec/traceability-proposal.md). 사용자 요청은 다음 구현 준비이며 준비 당시에는 제품 코드를 수정하지 않았다. 후속 사용자 요청으로 TASK-016 구현을 진행한다. 범위는 REQ–TASK 관계·연결 표·진단으로 한정한다.

실행자는 `superpowers:executing-plans`를 참고해 아래 단위를 순서대로 수행한다. 진행 상태·담당자는 GitHub에서 관리하고 이 문서에 중복 기록하지 않는다. 아래 번호는 준비 단위이며 아직 확정 TASK ID가 아니다.

## 다음 작업자가 시작할 순서

1. [개요 (DOC-016)](../01-prd/overview.md), [SDD 역할](../../rules/sdd-workflow.md), [GitHub 협업](../../rules/github-collaboration.md), DOC-025와 이 문서를 읽는다.
2. 사용자 설계 검토 결과를 확인한다. 이번 준비 요청을 미작성 설계의 승인이나 GitHub 등록·병합 승인으로 확대하지 않는다. 설계가 확정되면 아래 준비 단위 1부터 실행한다.
3. git status와 현재 dev를 비교한다. 2026-10-01 준비 당시 HEAD는 e141513이며 작성 하네스·서체·문서 탐색·ChecklistPage 변경이 미커밋으로 존재한다. 작업 시작 시 최신 상태를 다시 확인하고 그 변경을 보존한다.
4. vault의 `10_Projects/syncdoc/MOC.md`와 `2026-10-01-SDD-Reference-Research.md`를 읽는다. 외부 레퍼런스 설명보다 DOC-025의 확정 결과를 구현 기준으로 삼는다.

## 공통 제약

- React/TypeScript, Spring Boot 4.1.1, Java 25, commonmark 0.24.0, PostgreSQL 및 현재 lock 파일을 따른다. 새 파서·AI·그래프 의존성을 추가하지 않는다.
- frontmatter id/type/status 세 필드, Git 원문 정본, GitHub 진행 상태 정본을 유지한다.
- 관계는 동일 snapshot, 실행 상태는 기존 관찰 결과다. 열람 검증 전 데이터 조회·캐시 반환을 하지 않는다.
- 파싱 예외와 진단 오류를 구분한다. 원자적 게시·임대 token·마지막 정상 게시본 유지 원칙을 보존한다.
- 정책 버전 변경으로 새 분석 결과를 생성하고 기존 게시본에 소급 기록하지 않는다.

## 준비 단위 1: 명세 승격과 작업 등록

수정 대상: docs/01-prd/overview.md, mvp-scope.md; docs/02-ui-spec/ui-screens.md; docs/03-tech-spec/api-spec.md, data-model.md; docs/04-tasks/implementation-plan.md. 검사 경계가 바뀌면 rules/validation.md도 함께 검토한다.

1. 승인된 DOC-025 내용을 해당 정본에 분배한다. 요구·인수 기준은 PRD 한 곳, 화면 표현은 UI, 계약과 저장은 기술 명세에 두고 나머지는 링크한다.
2. 새 REQ/UI/API/TASK ID의 사용 여부를 검색하고 번호를 발급한다. 다음 번호를 이 문서만 보고 고정하지 않는다.
3. `python tools/spec-validator/validate.py`를 실행한다. 링크와 앵커는 별도 확인하고 내용 충족 검사를 통과했다고 보고하지 않는다.
4. 작업계획 검토 결과에 따라 기존 Issue 중복을 확인한 뒤 구현 단위별 TASK Issue를 등록한다. 구현 기준 revision 링크와 선행 Issue를 남긴다.

완료 조건: 승인된 요구·계약·저장·화면·완료 조건이 정본에 있고 작업 ID가 일관된다. 구현 준비 완료와 제품 구현 완료를 구분한다.

## 준비 단위 2: 순수 분석기

새 파일 후보(backend/src/main/java/io/github/unclesamsun/syncdoc/spec 아래): TraceabilityAnalyzer.java, SpecTraceability.java. 테스트: backend/src/test/java/io/github/unclesamsun/syncdoc/spec/TraceabilityAnalyzerTest.java.

인터페이스 후보: `SpecTraceability analyze(List<TraceabilitySource> sources)`. TraceabilitySource는 documentId/path/markdown/headings를 갖고 schemaVersion=1 보고서를 반환한다. 다른 준비 단위는 DOC-025의 입력·결과 형식을 사용한다. 보고서는 현재 sourceRefsJson/validationRefsJson과 이중 기록하지 않는다.

1. 실패하는 분석기 테스트를 먼저 작성한다. fixture의 확정 REQ-001·TASK-001 근거를 입력하면 edges가 한 개이며 렌더러 anchor를 그대로 보존해야 한다.
2. 범위 REQ-001 ~ REQ-008, 중복 참조, TASK 근거가 없는 REQ, 중복 REQ/TASK, 잘못된 로컬 링크 경로, 코드·예시·검토 문서 제외를 검사한다.
3. 근거 없는 라벨/빈 라벨/역순 범위/잘못된 ID가 partial과 unknown을 만들고, 유효한 요구에 연결 작업이 없을 때만 complete/unlinked가 되는 테스트를 둔다. 확정 요구 없음·작업 없음도 분리한다.
4. `(Set-Location backend 후) .\gradlew.bat test --tests '*TraceabilityAnalyzerTest'`로 실제 실패를 확인하고 최소 분석기를 구현해 같은 검사를 통과시킨다. 일반 Markdown 정규식 전체 스캔으로 코드 예시를 관계로 세지 않는다.

완료 조건: DB·네트워크 없이 관계와 진단을 결정적으로 재현한다. 입력 순서 변경이 결과 정렬을 바꾸지 않는다.

## 준비 단위 3: snapshot 저장과 수집 통합

새 파일 후보: backend/src/main/resources/db/migration/V8__spec_traceability.sql(착수 시 번호 재확인).
수정: document/domain/DocumentSnapshotEntity.java, document/DocumentVersions.java, sync/SyncWorker.java.
테스트: spec/TraceabilityPersistenceTest.java, sync/SyncCollectionTest.java와 SyncRecoveryTest.java의 관련 회귀.

1. 보고서 JSON 왕복, 기존 snapshot NULL/미분석, 다른 snapshot 격리, snapshot 삭제 시 동반 정리 테스트를 만든다.
2. 분석 완료 전에 게시본이 전환되지 않고 분석 예외·임대 상실 시 이전 정상 게시본을 유지하는 수집 테스트를 만든다. 진단 오류가 있는 보고서는 저장·게시할 수 있어야 한다.
3. nullable traceability_json과 접근자를 추가한다. 수집 중 문서 원문/실제 headings를 분석기에 전달하고 checklist와 같은 게시 트랜잭션에 결과를 저장한다. 분석 버전을 기존 정책 버전에 반영한다.
4. `.\gradlew.bat test --tests '*TraceabilityPersistenceTest' --tests '*SyncCollectionTest' --tests '*SyncRecoveryTest'`를 실행한다. 통합 시험은 기존 Testcontainers/Docker 조건을 따른다.

완료 조건: 같은 revision이라도 분석 정책 버전이 바뀌면 새 게시본이 생기고 과거 결과는 그대로 유지된다. raw Markdown을 새 DB 필드에 영속 저장하지 않는다.

## 준비 단위 4: 권한을 지키는 조회 API

새 파일 후보(spec 패키지): SpecTraceabilityService.java, SpecTraceabilityController.java.
재사용: ProjectService.view, DocumentService.snapshotFor, TaskMappingService.map.
테스트: spec/SpecTraceabilityApiTest.java.

1. 요구별 TASK·Issue 합성, Issue 중복 주장과 미등록, pagination/filter 및 정렬 테스트를 둔다. 관계 보고서는 같아도 이후 Issue 관찰 결과는 갱신되는지 확인한다.
2. 인증 없음·열람 불가·다른 프로젝트 snapshot·미완성/회수 snapshot·첫 수집 전·NULL 보고서·400 입력 오류를 테스트한다. 두 계정이 순서대로 같은 projectId를 요청해도 앞 계정 응답이 새 계정에 반환되지 않아야 한다.
3. DOC-025의 두 경로를 구현한다. 요청마다 권한 검증하고 private/no-store를 반환한다. JSON 결과 전체를 한 번에 내려주는 대신 응답 pagination을 적용한다.
4. `.\gradlew.bat test --tests '*SpecTraceabilityApiTest'`를 실행한다. 기존 작업 상태와 완료율 산식을 바꾸지 않는다.

완료 조건: 응답이 기준 snapshot과 실행 상태 관찰 시점을 모두 제공하며 분석 통과를 요구 충족으로 표현하지 않는다.

## 준비 단위 5: 기존 체크리스트 화면 연결

새 파일 후보: frontend/src/features/spec/TraceabilityPanel.tsx, useTraceability.ts, traceabilityTypes.ts; frontend/tests/traceability.test.tsx.
수정: frontend/src/features/spec/ChecklistPage.tsx. AppShell의 새 메뉴나 별도 route는 만들지 않는다.

1. REQ 행·TASK·Issue·원문 이동, 필터·페이지 이동, 진단 위치 표시를 테스트한다.
2. complete/partial/unchecked, 요구 없음·작업 없음, 409/404/410/통신 실패를 구분하고 기존 산출물 오류 건수가 새 진단과 합산되지 않는지 검증한다.
3. 프로젝트 또는 snapshot 전환 후 늦게 도착한 이전 응답이 덮어쓰지 않는 테스트를 둔다. 요구 목록과 진단 요청은 동일 snapshotId를 사용한다. 미분석을 0건 통과로 표시하지 않는다.
4. `npm run test -- --run tests/traceability.test.tsx tests/checklist.test.tsx`의 실패를 확인하고 기존 스타일·표·문서 링크 패턴으로 패널을 구현한다. 실제 원문 anchor와 snapshotId를 링크에 포함한다.
5. 같은 테스트와 `npm run build`를 실행한다. 작은 화면·긴 제목·긴 진단 목록·키보드 이동을 실제 브라우저에서 확인한다.

완료 조건: 기존 체크리스트 흐름에서 요구와 작업 관계를 읽고 원문으로 이동한다. 그래프·새 메뉴·웹 편집이 추가되지 않는다.

## 준비 단위 6: 인수 검증과 인계

1. Java 25를 설정해 backend에서 `.\gradlew.bat test`, frontend에서 `npm run test -- --run`, `npm run build`, 저장소 루트에서 `python tools/spec-validator/validate.py`를 실행한다. Python 검사기 자체를 바꿨다면 tools/spec-validator에서 `python -m unittest test_validate -v`도 실행한다.
2. frontend/tests/mvp.spec.ts의 기존 구성에 관계 fixture를 추가해 요구→작업→원문 이동·새 게시본 전환을 확인한다. 실행 환경과 로그인은 README의 기존 E2E 절차를 따른다. API 모킹 시험과 실제 연결 저장소 시험을 구분한다.
3. 실사용 테스트 저장소에서 관계 1건, 없는 ID 1건, 부분 분석 1건을 확인하고 기존 문서/작업/체크리스트 흐름의 회귀가 없는지 확인한다. 비공개 프로젝트 접근 거부도 확인한다.
4. PR에 실제 검사 결과·미실행 항목·영향 영역·기준 revision·관련 TASK Issue를 남긴다. 로컬 테스트 성공을 dev 병합·배포 성공으로 표시하지 않는다.
5. vault 프로젝트 MOC에 구현·검증한 결과와 남은 사항을 갱신한다. 후속 후보는 UI/API 관계와 변경 영향이며 이번 PR에 섞지 않는다.

## 자체 검토

DOC-025 인수 조건은 연결/잘못된 참조/부분 분석(단위 2), 게시·기존 게시본(단위 3), 접근·시간 기준(단위 4), 사용자 표시·이동(단위 5), 실제 흐름(단위 6)으로 연결했다. 현재 C0/C1/C2와 Issue 완료율은 유지한다. 새 계약과 저장 변경은 단위 1에서 승인 정본으로 승격한 뒤 제품 구현을 시작한다.
