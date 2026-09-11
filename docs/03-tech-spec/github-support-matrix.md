---
id: DOC-019
type: proposal
status: 검토
---

# GitHub 지원 매트릭스

TASK-001의 산출물이다. MVP가 GitHub에서 무엇을 읽어야 하고, 그것이 실제로 되는지 확인했는지를 한 표로 관리한다. 확인한 행은 확인 일자를 적고, 전부 확인되면 이 문서를 `record`로 바꾼다.

2026-09-10에 GitHub App을 등록하고 실제 로그인을 한 번 성공시켰다. 2026-09-11에 실제 저장소를 연결했다. 그 흐름이 쓰는 행만 확인으로 바꿨고 나머지는 그대로 미확인이다. 앱을 등록했다는 사실이 각 API가 된다는 뜻은 아니다.

## 무엇을 제안하는가

GitHub App의 사용자 인증과 설치 인증 조합을 채택하고, 필요한 권한을 아래 표로 고정한다. 표의 권한은 GitHub 문서를 근거로 한 예상값이며 연결 실험에서 확정한다.

| 필요 기능 | 근거 요구 | 사용하는 API | 필요 권한 (예상) | 확인 |
|---|---|---|---|---|
| 사용자 신원 (ID·login) | REQ-001 | `GET /user` (사용자 토큰) | 없음 (사용자 토큰 기본) | **확인 2026-09-10** |
| 앱·사용자 모두 접근 가능한 저장소 목록 | REQ-002, REQ-007 | `GET /user/installations`, `GET /user/installations/{id}/repositories` | Metadata: read | **확인 2026-09-11** |
| 브랜치 목록·경로 존재 확인 | REQ-002, REQ-006 | `GET /repos/{o}/{r}/branches`, `GET /repos/{o}/{r}/contents/{path}?ref=` (사용자 토큰) | Contents: read | **확인 2026-09-11** |
| 설치 토큰 발급 | REQ-006 | `POST /app/installations/{id}/access_tokens` (App JWT, RS256) | 없음 (App 자격증명) | **확인 2026-09-11** |
| 문서 파일 읽기 | REQ-004, REQ-006 | `GET /repositories/{id}`, `GET /repos/{o}/{r}/commits/{ref}`, `GET /repos/{o}/{r}/contents/{path}?ref=`, `GET /repos/{o}/{r}/git/blobs/{sha}` (설치 토큰) | Contents: read | **확인 2026-09-11** |
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

## 2026-09-11에 확인한 것

실제 저장소 `UncleSamsun/syncdoc`을 브라우저에서 연결했다.

- **교집합을 서버가 계산할 필요가 없다.** 사용자 토큰으로 `GET /user/installations/{id}/repositories`를 부르면 GitHub가 앱 설치 접근과 사용자 접근이 모두 되는 저장소만 돌려준다. 서버가 따로 교집합을 계산하면 GitHub의 판단과 어긋날 수 있다.
- **브랜치 목록과 경로 확인이 사용자 토큰으로 된다.** 설치 토큰이 아직 필요하지 않다. `Contents: read`만으로 `GET /repos/{o}/{r}/branches`와 `contents/{path}?ref=`가 동작한다.
- **저장소 ID가 이름과 독립이다.** `UncleSamsun/syncdoc`이 `1362321768`로 저장됐다. 저장소 이름이 바뀌어도 프로젝트는 같다.
- **설치 하나가 소유자 단위로 기록된다.** installation `160552187`이 `UncleSamsun` 소유로 잡혔다.
- 서버 로그에 토큰·client secret·암호화키가 한 건도 없고 예외도 0건이었다. 중복 연결과 없는 경로는 계약대로 409·422로 처리되어 예외가 로그로 새지 않았다.

## 2026-09-11에 확인한 것 (수집)

TASK-005에서 실제 저장소 `UncleSamsun/syncdoc`을 사용자 없이 수집했다. 로그인한 사용자가 없는 상태에서 worker만 돌렸다.

- **설치 토큰으로 수집이 된다.** App private key로 만든 RS256 JWT로 `POST /app/installations/160552187/access_tokens`를 부르고, 받은 토큰으로 문서를 읽었다. 사용자 토큰은 한 번도 쓰지 않았다. TASK-003이 남긴 "설치 토큰이 필요한 지점" 미결을 닫는다.
- **GitHub가 주는 키는 PKCS#1이다.** `-----BEGIN RSA PRIVATE KEY-----` 형식이라 자바 표준 `KeyFactory`가 바로 읽지 못한다. PKCS#8로 감싸서 쓴다. 두 형식 모두 테스트로 확인했다.
- **문서 경로 아래만 훑는다.** `main`의 `docs` 아래 Markdown 11개를 수집했고 실제 파일 수와 같다. 하위 디렉터리 네 곳까지 내려갔다. 저장소 전체 트리를 받지 않으므로 GitHub가 목록을 자르는 경우를 만들지 않는다.
- **바뀐 것이 없으면 다시 변환하지 않는다.** 다음 주기 조회는 같은 revision을 보고 `{"unchanged":true}`로 1초 만에 끝났다. 새 게시본을 만들지 않았다.
- **연결 설정을 바꾸면 새 게시본이 생기고 이전 것이 남는다.** 기준 브랜치를 `dev`로 바꾸자 revision `15810de3`의 게시본(문서 19개)이 현재가 되고, `main` 게시본(11개)은 지워지지 않은 채 현재가 아닌 상태로 남았다.
- **저장소 해소는 수집당 한 번이면 된다.** 처음에는 문서마다 `GET /repositories/{id}`를 불러 11개 수집에 13.3초가 걸렸다. 한 번만 해소하도록 고쳐 8.8초가 됐고 요청 수도 문서 수만큼 줄었다.
- **webhook은 비밀값이 없으면 받지 않는다.** 실제 서버에 서명 없는 push 본문을 보내니 503 `WEBHOOK_NOT_CONFIGURED`로 거절했다. 확인하지 못한 요청을 처리하지 않는다.
- 서버 로그에 설치 토큰(`ghs_`)·사용자 토큰(`gho_`)·App JWT·private key·client secret이 한 건도 없고 예외도 0건이었다.

## 확정되지 않은 항목

- 조직 소유 저장소의 앱 설치 승인 절차와 조직의 앱 접근 제한 정책
- 개인 Project와 조직 Project의 GraphQL 권한 차이, 사용자 토큰만으로 조회 가능한 범위
- webhook 실제 delivery. 서명 검증은 구현하고 테스트로 재현했지만 GitHub가 실제로 보낸 delivery는 아직 받아 보지 않았다. 공개 URL이 필요하며 TASK-008의 호스팅 검증으로 남긴다
- 요청 제한(429)에 실제로 걸렸을 때 GitHub가 주는 헤더. 코드는 `retry-after`와 `x-ratelimit-reset`을 모두 읽지만 실제 제한 상황을 만들어 확인하지는 않았다
- 사내 서버에서 webhook 수신이 불가능할 때 주기 조회만으로 REQ-006 인수 기준을 만족하는지

## 다음

1. TASK-006에서 문서 변환을 만들며 첨부·이미지 읽기 범위를 확인한다.
2. TASK-007에서 Issue·PR·Project 행을 확인한다.
3. 서로 다른 권한의 테스트 계정 2개로 권한 교집합을 검증한다. 지금은 계정이 하나뿐이라 "보이지 않는 저장소" 경로를 fake로만 확인했다.
4. 확인 결과가 예상 권한과 다르면 [접근 설계](access-proposal.md)와 이 표를 고친다.
