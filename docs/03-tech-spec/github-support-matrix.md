---
id: DOC-019
type: proposal
status: 검토
---

# GitHub 지원 매트릭스

TASK-001의 산출물이다. MVP가 GitHub에서 무엇을 읽어야 하고, 그것이 실제로 되는지 확인했는지를 한 표로 관리한다. 2026-09-10 현재 **실제 GitHub App 등록과 테스트 계정이 없어 어느 행도 확인하지 않았다.** 이 문서가 검토 상태인 이유다. 확인한 행은 확인 일자와 방법을 적고, 전부 확인되면 이 문서를 `record`로 바꾼다.

## 무엇을 제안하는가

GitHub App의 사용자 인증과 설치 인증 조합을 채택하고, 필요한 권한을 아래 표로 고정한다. 표의 권한은 GitHub 문서를 근거로 한 예상값이며 연결 실험에서 확정한다.

| 필요 기능 | 근거 요구 | 사용하는 API | 필요 권한 (예상) | 확인 |
|---|---|---|---|---|
| 사용자 신원 (ID·login) | REQ-001 | `GET /user` (사용자 토큰) | 없음 (사용자 토큰 기본) | 미확인 |
| 앱·사용자 모두 접근 가능한 저장소 목록 | REQ-002, REQ-007 | `GET /user/installations`, `GET /user/installations/{id}/repositories` | Metadata: read | 미확인 |
| 브랜치·경로 존재 확인, 문서 파일 읽기 | REQ-002, REQ-004, REQ-006 | `GET /repos/{o}/{r}/branches/{b}`, `GET /repos/{o}/{r}/contents/{path}`, `GET /repos/{o}/{r}/git/trees/{sha}` (설치 토큰) | Contents: read | 미확인 |
| Issue·PR 상태·담당자 | REQ-003 | `GET /repos/{o}/{r}/issues`, `GET /repos/{o}/{r}/pulls` | Issues: read, Pull requests: read | 미확인 |
| Project(v2) 상태·필드 | REQ-003, REQ-007 | GraphQL `projectV2` (사용자 토큰) | Projects: read (조직 Project는 조직 설치 필요) | 미확인 |
| push·issues·pull_request webhook | REQ-006 | App webhook | 이벤트 구독: push, issues, pull_request, projects_v2_item | 미확인 |
| 요청자의 저장소 권한 확인 | REQ-007 | `GET /repos/{o}/{r}` (사용자 토큰) 또는 `GET /repos/{o}/{r}/collaborators/{u}/permission` | Metadata: read | 미확인 |
| rate limit·재시도 시각 | REQ-006 | 응답 헤더 `x-ratelimit-*`, `retry-after` | 없음 | 미확인 |

## 확정되지 않은 항목

- 조직 소유 저장소의 앱 설치 승인 절차와 조직의 앱 접근 제한 정책
- 개인 Project와 조직 Project의 GraphQL 권한 차이, 사용자 토큰만으로 조회 가능한 범위
- 사용자 토큰 만료·갱신 방식 (GitHub App 사용자 토큰은 만료가 선택 설정이다)
- 사내 서버에서 webhook 수신이 불가능할 때 주기 조회만으로 REQ-006 인수 기준을 만족하는지

## 다음

1. 사용자가 GitHub App을 등록하고 `deploy/.env.example`의 `SYNCDOC_GITHUB_*` 값을 로컬에 둔다.
2. 서로 다른 권한의 테스트 계정 2개로 위 표의 각 행을 read-only로 호출해 확인 일자와 결과를 채운다.
3. 확인 결과가 예상 권한과 다르면 [접근 설계](access-proposal.md)와 이 표를 고친다.
