---
id: DOC-028
type: proposal
status: 검토
---

# 게시본 비교 실행 계획

2026-10-02 사용자 요청: 설계 DOC-027을 기준으로 계획을 작성하고 곧바로 구현한다. 실행 방식은 이 세션에서 직접 수행하는 executing-plans다. 아래 단위는 하나의 TASK-017 안 작업 순서이며 별도 업무 ID가 아니다. 실행 상태는 GitHub에서 관리한다.

## 목표·구성

동일 프로젝트/branch/docsRoot의 불변 게시본 두 개를 비교하고 문서·REQ/TASK 원문 변화와 재검토 TASK를 읽는다. 수집 중 scope와 ComparisonIndex v1을 저장하고, 조회는 권한 확인 후 순수 ComparisonEngine 결과와 현재 TaskMappingService를 합성한다. 기존 traceability v1·C0/C1/C2·완료율은 그대로 둔다.

기술: 현재 Java25/Spring Boot4.1.1/commonmark0.24/PostgreSQL/React/TypeScript/lock파일. 새로운 파서·AI·그래프 라이브러리는 추가하지 않는다. 원문 전문·사용자 자격증명을 새 저장 필드나 로그에 복제하지 않는다. 문서 정본은 Git, 실행 상태는 GitHub, 보고서는 파생 데이터다.

## 검토 초점

- 제목은 같고 본문만 수정: sectionHash 변화와 REQ 영향 후보를 놓치지 않는다.
- 같은commit 다른docsRoot: scoped 재사용 키가 분리되고 과거 결과가 유지된다.
- NULL/다른scope/중복/partial: 변경 없음·영향 없음으로 단정하지 않는다.
- DOC ID 교체·항목 위치 이동: 경로만으로 다른 정본을 같은 것으로 합치지 않는다.
- 사용자/게시본 전환: 양쪽 권한을 검사하고 늦은 이전 응답을 배제한다.

## 1. 정본과 GitHub 착수

대상: overview.md,mvp-scope.md,ui-screens.md,api-spec.md,data-model.md,implementation-plan.md.

REQ-010/UI-017/API-029~033/TASK-017의 사용 여부를 검색하고 승인 범위를 정본에 분배한다. DOC-027은 검토 경위로 유지한다. doc 검사와 링크 경로 검사를 실행한 뒤 Issue를 중복 확인·등록하고 dev 기반 작업 브랜치에서 수행한다.

## 2. 순수 항목 인덱스

새 파일: spec/ComparisonIndex.java,ComparisonIndexer.java 및 ComparisonIndexerTest.java.

입력은 TraceabilityAnalyzer.Source(documentId,path,markdown,headings). 출력은 ComparisonIndex(schemaVersion1,fingerprintAlgorithm markdown-section-lf-v1,indexStatus,documents 상태,items,findings). Item은kind/id/documentId/title/anchor/line/sectionHash다.

먼저 AST H2 실제 구간의 본문만 변화·LF/CRLF·코드 속 가짜 제목·확정 상태 변경·중복 ID·H1/H2 경계·제목 밖 수정 테스트를 작성하고 실패를 확인한다. 형식/검토 규약은 DOC-027을 따른다. 구현 후 `gradlew test --tests '*ComparisonIndexerTest'`를 통과시킨다.

## 3. scope 저장과 수집

새 파일: db/migration/V9__snapshot_comparison.sql. 수정: DocumentSnapshotEntity/Repository,DocumentVersions,SyncWorker. 테스트: ComparisonPersistenceTest + 기존 SyncCollection/SyncRecovery.

nullable collection_branch/collection_docs_root/comparison_json, unique key의scope확장,scoped 조회를 추가한다. 새 생성자만 실제 scope를 저장하며 legacy 생성자는NULL로 보존한다. policy를올리고 traceability저장과같은게시전단계에ComparisonIndexer를실행한다.

같은revision의다른root·새policy·JSON왕복·NULL legacy·분석예외·임대상실·과거보고서불변의시험을먼저실패시킨뒤수집과migration을구현한다. scoped 키를입력으로받는repository메서드는SyncWorker만새재사용조회에사용한다. 기존테스트의구버전조회는필요한경우별도로유지한다.

## 4. 비교·영향 순수 계산

새 파일: ComparisonEngine.java,ComparisonEngineTest.java.

입력은각쪽Document 자료(UUID/specId/path/title/kind/sourceHash/status),ComparisonIndex,SpecTraceability. 출력은documents/items 변경행(before/after/change/reason),impacts(requirementId/taskId/reason),status/coverage/findings다. 기본row키는문서 DOC 또는path,항목kind+itemId다.

추가/제외/이동/원문변경/이동+변경/동일/unknown,ID변경·무ID·duplicate·partial·해시버전·역방향비교,양쪽관계union,Task자체변경과Req영향구분·삭제Task·Issue미관찰테스트를작성한다. known후보0은완료증명이아니다. `gradlew test --tests '*ComparisonEngineTest'`로확인한다.

## 5. API 합성과 권한

새 파일: SnapshotComparisonService/Controller.java,SnapshotComparisonApiTest.java. 재사용: ProjectService.view,DocumentService.snapshotFor,DocumentRepository,TaskMappingService.map.

API-029 완료목록,030요약,031문서,032항목,033영향을구현한다. 요청마다권한→양쪽snapshot→scope→index판정을수행하고no-store/private를반환한다. 모든페이지에고정from/to메타정보를보낸다.

필수ID/400필터/페이지overflow/정렬,첫게시본없음·legacy unchecked counts=null·409scope·404프로젝트·410다른/회수/미완성snapshot·401·다른사용자캐시재사용없음·현재Issue갱신과과거관계고정의HTTP/Postgres시험을실행한다.

## 6. 화면

새 파일: features/spec/ComparisonPage.tsx,comparisonTypes.ts와tests/comparison.test.tsx. 수정App.tsx와ChecklistPage 진입link. 별도메뉴는추가하지않는다.

완료게시본목록의page이동·from/to선택,비교요약·문서/항목/영향탭과change/kind필터·페이지이동을제공한다. metadata와row요청은같은두ID를사용하며AbortController와요청key로stale응답을배제한다. 양쪽원문에는각snapshotId/actualanchor를넣고실행상태관찰시각을분리한다.

미확인/부분/범위차이/권한/410/통신오류/정상변경0을시험한다. 같은scope 최신/직전비교가능후보를추천하되사용자가다른page의완료게시본을선택할수있다. 모바일내부표스크롤·키보드·기존sidebar를유지한다.

## 7. 검증과 PR

backend전체test,frontend전체test/build,문서검사를실행한다. fresh context의전체브랜치리뷰1회를요청하고중요한발견은실패재현시험→수정→전체검증으로마무리한다. source/body-only·doc이동·partial·legacy·권한·scope재수집을실제HTTP/Postgres에서확인한다. 실제회사서버의새기능시험은dev/main최종병합판단과배포후단계이며fixture시험과구분한다.

PR에는영향영역·검증/미실행·후속사항·Issue·정본링크를넣고Vault를갱신한다. 단계완료와main배포완료는별도다. 이번사용자의직접구현승인에따라계획작성후추가착수질문없이진행한다.
