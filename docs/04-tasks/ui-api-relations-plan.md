---
id: DOC-030
type: proposal
status: 검토
---

# UI·API 관계 실행 계획

설계는 [DOC-029](../03-tech-spec/harness-development-design.md), 활성 계약은 REQ-011/UI-018/API-034~035/TASK-018이다. 사용자는 세 후보를 이 세션에서 직접 계획·구현·리뷰·PR·병합하도록 승인했다. 마지막 전체 통합 후 E2E 한 번을 수행한다. 지금 브라우저 E2E나 배포는 하지 않는다.

## 검토 초점

- UI-000과 API 설명 H2를 정의 중복으로 오인하지 않는다.
- 같은 문단의 '연결 요구'와 '관련 작업'을 섞지 않는다.
- 코드/HTML 예시·잘못된 범위·없는/중복 정의는 유효 관계가 아니다.
- legacy/partial에서 연결0을 적용 제외·충족·영향 없음으로 표시하지 않는다.
- 과거 snapshot 관계와 실제 앵커를 고정하고 현재 Issue 관찰을 분리한다.

## 1. 계약과 착수

DOC-029/030과 REQ-011·UI-018·API-034/035·TASK-018을 기존 정본에 연결하고 문서 검사. 승인된 TASK의 Issue를 등록하고 dev 기반 feat/<issue>-ui-api-relations로 작업한다. 기존 managed worktree를 재사용하며 다른 프로젝트는 수정하지 않는다.

## 2. 순수 AST 관계 분석

새 SpecRelations.java: schemaVersion/analysisStatus/uncheckedReason/nodes/edges/findings. Node(kind,itemId,title,documentId,path,anchor,line), Edge(sourceKind,sourceId,targetKind,targetId,relation,sourceLocation). 별도 RelationAnalyzer.analyze(List<TraceabilityAnalyzer.Source>,SpecTraceability).

기존 visible/expand/path 도우미는 package 범위로 재사용해 기존 v1 결과를 바꾸지 않는다. UI 실제 H2와 API 계약 일람 table row, 각 라벨/표를 정본으로 읽는다. API H2는 앵커 보조로만 쓴다. 순수 테스트: UI000·API 설명 중복 없음, 요구/UI/API/TASK 경로, 코드/HTML 무시, 같은 줄 라벨 분리, 잘못된 범위/중복/없는 대상/path mismatch, 선행 순환, 확정 상태만, 실제 line/anchor. RED→GREEN.

## 3. 수집·저장

V10__spec_relations.sql에 nullable relations_json만 추가. DocumentSnapshotEntity getter/setter와 SyncWorker 분석 게시 전 저장, DocumentVersions 정책 증가. 관계 분석 실패/임대 상실에서 이전 정상 게시본 보존. 새 정책 재수집과 과거 결과 불변, legacy 미분석을 기존 수집/회복 시험과 함께 검증한다.

## 4. API와 변경 영향

RelationService는 projects.view→documents.snapshotFor로 요청마다 권한/게시본을 확인한다. GET /spec-relations?snapshotId=&page=0&size=50의 requirement rows는 각 요구·UI/APIs/TASKs·현재 실행 관찰과 진단을 제공한다. size1..100/page>=0, overflow 안전, metadata는 같은 snapshot이다. 연결 없는 설계는 적용 여부를 판정하지 않는다.

GET /snapshot-comparison/design-impacts는 기존 비교 엔진의 변경 REQ와 old/new relations의 UI/API 역참조 합집합을 페이지로 제공한다. before/after 각각 snapshot 고정 링크, 자료 없으면 unchecked이다. API400/401/404/409/410/private-no-store·legacy·현재 관찰과 과거 관계·본문만 변경·pure move 제외 HTTP/Postgres 시험.

## 5. 화면

산출물 안 '요구·설계·작업' 표와 기존 비교 화면 '설계 재검토' 탭. 늦은 응답 취소, project/snapshot/pair 전환 초기화와 actual anchor/source snapshot 링크, 필터/페이지·부분/미분석/오류를 검증한다. 기존 메뉴를 늘리거나 미관을 새로 설계하지 않는다. 프런트 컴포넌트 시험을 먼저 실패시키고 구현한다.

## 6. 리뷰·PR·병합

백엔드 전체 test·프런트 test/build·문서 검사. fresh-context 전체 브랜치 리뷰1회, 중요한 발견은 RED→GREEN과 전체 회귀. Issue/정본/검증/E2E 최종 보류를 PR에 기록하고 dev에 squash 병합. 실행 상태는 GitHub에서 관리하고 vault에 확정 사실을 기록한다. 다음 단계는 새 dev 기준 작업 컨텍스트 계획 작성이다.
