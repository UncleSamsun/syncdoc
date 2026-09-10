---
id: DOC-019
type: proposal
status: 검토
---

# GitHub 지원 매트릭스

TASK-001의 산출물이다. MVP가 GitHub에서 무엇을 읽어야 하고, 그것이 실제로 되는지 확인했는지를 한 표로 관리한다. 확인한 행은 확인 일자를 적고, 전부 확인되면 이 문서를 `record`로 바꾼다.

2026-09-10에 GitHub App을 등록하고 실제 로그인을 한 번 성공시켰다. 그 흐름이 쓰는 행만 확인으로 바꿨고 나머지는 그대로 미확인이다. 앱을 등록했다는 사실이 각 API가 된다는 뜻은 아니다.

## 무엇을 제안하는가

GitHub App의 사용자 인증과 설치 인증 조합을 채택하고, 필요한 권한을 아래 표로 고정한다. 표의 권한은 GitHub 문서를 근거로 한 예상값이며 연결 실험에서 확정한다.

| 필요 기능 | 근거 요구 | 사용하는 API | 필요 권한 (예상) | 확인 |
|---|---|---|---|---|
| 사용자 신원 (ID·login) | REQ-001 | `GET /user` (사용자 토큰) | 없음 (사용자 토큰 기본) | **확인 2026-09-10** |
| 앱·사용자 모두 접근 가능한 저장소 목록 | REQ-002, REQ-007 | `GET /user/installations`, `GET /user/installations/{id}/repositories` | Metadata: read | 미확인 |
| 브랜치·경로 존재 확인, 문서 파일 읽기 | REQ-002, REQ-004, REQ-006 | `GET /repos/{o}/{r}/branches/{b}`, `GET /repos/{o}/{r}/contents/{path}`, `GET /repos/{o}/{r}/git/trees/{sha}` (설치 토큰) | Contents: read | 미확인 |
| Issue·PR 상태·담당자 | REQ-003 | `GET /repos/{o}/{r}/issues`, `GET /repos/{o}/{r}/pulls` | Issues: read, Pull requests: read | 미확인 |
| Project(v2) 상태·필드 | REQ-003, REQ-007 | GraphQL `projectV2` (사용자 토큰) | Projects: read (조직 Project는 조직 설치 필요) | 미확인 |
| push·issues·pull_request webhook | REQ-006 | App webhook | 이벤트 구독: push, issues, pull_request, projects_v2_item | 미확인 |
| 요청자의 저장소 권한 확인 | REQ-007 | `GET /repos/{o}/{r}` (사용자 토큰) 또는 `GET /repos/{o}/{r}/collaborators/{u}/permission` | Metadata: read | 미확인 |
| rate limit·재시도 시각 | REQ-006 | 응답 헤더 `x-ratelimit-*`, `retry-after` | 없음 | 미확인 |

## 2026-09-10에 확인한 것

등록한 앱은 `UncleSamsun` 계정 전용이고 webhook은 꺼 두었다. 콜백은 개발 서버 경유(`http://localhost:5173/api/v1/auth/github/callback`)와 백엔드 직접, 두 개를 등록했다.

- **PKCE가 동작한다.** 인증 요청에 `code_challenge`와 `code_challenge_method=S256`을 붙였고 토큰 교환이 `code_verifier`로 성공했다. 계약의 "지원되는 PKCE 사용"은 GitHub App에서 실제로 지원된다.
- **사용자 토큰은 만료된다.** 등록 시 만료를 켰고 GitHub가 8시간짜리 access token과 갱신 토큰을 함께 준다. 세션 수명 12시간이 토큰 수명보다 길어, 세션이 살아 있는데 토큰이 죽는 구간이 생긴다. 갱신 호출은 포트에 구현했고 **언제 갱신할지는 아직 정하지 않았다.** GitHub를 실제로 부르는 첫 계약(API-008)에서 정한다.
- **저장이 설계대로다.** 최초 관리자 초대가 `granted_by`가 빈 채로 자동 생성됐고, 사용자는 GitHub 숫자 ID로 저장됐다. access·refresh 토큰은 암호문으로, 세션은 해시로만 남았다. 서버 로그에 토큰·client secret·암호화키가 한 건도 나오지 않았다.

## 확정되지 않은 항목

- 조직 소유 저장소의 앱 설치 승인 절차와 조직의 앱 접근 제한 정책
- 개인 Project와 조직 Project의 GraphQL 권한 차이, 사용자 토큰만으로 조회 가능한 범위
- 사용자 토큰 갱신 시점. 만료가 켜진 것은 확인했고 언제 갱신할지가 남았다
- 사내 서버에서 webhook 수신이 불가능할 때 주기 조회만으로 REQ-006 인수 기준을 만족하는지

## 다음

1. TASK-003에서 저장소 목록(API-008)을 구현하며 설치 접근과 사용자 접근의 교집합 행을 확인한다. 그때 토큰 갱신 시점도 정한다.
2. 서로 다른 권한의 테스트 계정 2개로 남은 행을 read-only로 호출해 확인 일자를 채운다. 지금은 계정이 하나뿐이라 권한 교집합을 검증하지 못했다.
3. 확인 결과가 예상 권한과 다르면 [접근 설계](access-proposal.md)와 이 표를 고친다.
