---
id: DOC-010
type: tech-interface
status: 확정
---

# MVP API 계약

아직 서버가 없다. 이 계약은 구현 대상이며 동작하는 엔드포인트가 아니다.

[기능과 인수 기준](../01-prd/mvp-scope.md)의 요구를 API 경계로 옮긴 문서다. 계약마다 어느 요구에서 나왔는지는 계약 일람의 `연결 요구` 열에 있다. 다른 문서에서 계약을 가리킬 때는 메서드·경로가 아니라 `API-NNN`을 쓴다. 경로는 구현 중에 바뀔 수 있고 ID는 바뀌지 않으므로, ID로 참조하면 경로가 변해도 참조가 끊기지 않는다. ID 발급 규칙은 [문서 작성 규칙](../../rules/spec-writing.md) §6에 있다.

## 공통

경로 접두어는 `/api/v1`이다. 공개 객체 식별자는 UUID이고 GitHub ID는 별도로 보존한다. 숫자 GitHub ID는 JSON에서 문자열로 전달한다. 시간은 UTC ISO 8601이고, 목록은 기본 20·최대 100이며, cursor는 서버가 발급한 불투명 문자열이다.

아래 항목은 계약 대부분에 같게 적용된다. 계약마다 같은 문장을 반복하지 않고, 다른 계약만 그 계약의 절에 예외를 적는다.

**접근 조건:** 동일 origin의 HttpOnly·Secure·SameSite=Lax 세션 쿠키를 쓴다. 상태를 바꾸는 요청은 CSRF 토큰을 검사한다. 사용자 허용 목록과 GitHub 리소스 권한을 서버에서 확인한다. 세션 없이 부를 수 있는 계약은 로그인 시작·콜백(API-001·API-002), 서명을 검증하는 API-021, 상태 확인 API-022·API-023뿐이다. 관리자만 부를 수 있는 계약은 API-005·API-006·API-007이고, 연결자 또는 관리자만 부를 수 있는 계약은 API-012·API-013이다.

**부작용:** `GET` 계약은 서비스 상태도 GitHub 상태도 바꾸지 않는다. 상태를 바꾸는 계약은 API-004·API-006·API-007·API-009·API-012·API-013·API-021뿐이며, 이들도 GitHub에는 쓰지 않는다. 읽기 API가 GitHub에 상태를 쓰지 않는다는 것이 이 문서 전체의 전제다. Issue 발행은 승인된 에이전트의 GitHub 협업 작업이며 서비스 API로 구현하지 않는다.

**재시도:** 같은 입력으로 다시 불러도 결과가 달라지지 않아야 한다. 동기화 예약(API-013)은 활성 작업이 있으면 새 작업을 만들지 않고 기존 작업에 합쳐 같은 `jobId`를 돌려준다. 초대 등록(API-006)은 같은 계정이면 새로 만들지 않고 기존 항목을 200으로 돌려준다. webhook(API-021)은 중복 delivery를 202로 받고 같은 작업을 두 번 예약하지 않는다. 429에는 `Retry-After`를 함께 준다.

**오류:** 형식은 `{ "code":"RESOURCE_NOT_FOUND", "message":"대상을 찾을 수 없습니다.", "requestId":"...", "details":{} }`다. 권한 없는 비공개 대상과 없는 대상은 구분하지 않고 같은 404로 답한다. 미로그인 401, 초대·관리 작업 거절 403, 충돌 409, 입력 오류 422, 요청 제한 429, GitHub 확인 불가 503이다. `details`에 비공개 이름이나 토큰을 넣지 않는다.

HTML과 검색 결과는 `Cache-Control: private, no-store`로 제공한다. 사용자 GitHub 토큰·설치 토큰·원시 OAuth 응답은 어느 계약에서도 반환하지 않는다. 관리 대상 임의 URL은 받지 않는다. 상세 상태와 관리 actuator는 외부에 공개하지 않는다.

## 계약 일람

MVP가 구현할 계약 24개 전부다. 이 표에 없는 엔드포인트는 구현 대상이 아니다. 조건이 많은 계약은 아래에 같은 ID의 절을 두고, 표의 `응답·조건` 칸에서 그 절을 가리킨다.

| ID | 메서드·경로 | 연결 요구 | 입력 | 응답·조건 |
|---|---|---|---|---|
| API-001 | `GET /auth/github/start` | REQ-001 | returnTo: 허용된 서비스 내부 경로만 | 302 GitHub 로그인. 일회용 state와 지원되는 PKCE 사용 |
| API-002 | `GET /auth/github/callback` | REQ-001, REQ-007 | code, state | state 일치·만료 검사와 허용 목록 확인 후 세션 회전과 302. 실패 시 세션을 발급하지 않는다. 조건은 [API-002](#api-002-로그인-콜백) |
| API-003 | `GET /me` | REQ-001 | 없음 | 200 `{id,githubUserId,login,serviceAdmin,csrfToken}` |
| API-004 | `POST /logout` | REQ-001 | CSRF 토큰 | 204 세션 무효화 |
| API-005 | `GET /invitations` | REQ-001, REQ-007 | cursor | 허용 목록과 각 항목의 GitHub ID·상태 |
| API-006 | `POST /invitations` | REQ-001 | `{githubLogin}` | GitHub에서 ID를 확인한 뒤 허용 목록에 등록. 201, 같은 계정이면 기존 항목 200. 이메일은 보내지 않는다 |
| API-007 | `DELETE /invitations/{id}` | REQ-001, REQ-007 | 없음 | 204 허용 취소와 해당 사용자 세션 무효화. 최초 관리자는 취소할 수 없어 409 |
| API-008 | `GET /github/repositories` | REQ-002, REQ-007 | cursor | 앱 접근과 사용자 접근의 교집합 `{githubRepositoryId,fullName,private,defaultBranch}` |
| API-009 | `POST /projects` | REQ-002 | `{githubRepositoryId,branch,docsRoot,githubProjectNodeId?}` | 201 `{id,syncState:"queued"}`. 조건은 [API-009](#api-009-프로젝트-연결) |
| API-010 | `GET /projects` | REQ-002, REQ-007 | cursor | 허용된 프로젝트 목록과 최소 sync 상태. 권한 확인에 실패한 항목을 비인가 사용자에게 노출하지 않고, 결과가 불완전한지를 표시한다 |
| API-011 | `GET /projects/{id}` | REQ-002 | 없음 | 연결 설정, 현재 snapshotId, sync 상태·시각. 권한이 없으면 404 |
| API-012 | `PATCH /projects/{id}` | REQ-002, REQ-007 | `{branch?,docsRoot?,githubProjectNodeId?,expectedVersion}` | 200 새 version과 동기화 예약. 버전 충돌 409. 현재 GitHub 접근도 필요하다 |
| API-013 | `POST /projects/{id}/sync` | REQ-006 | 없음 | 202 `{jobId,reused}`. 활성 작업이 있으면 합친다 |
| API-014 | `GET /projects/{id}/sync` | REQ-006 | 없음 | `{state,lastAttemptAt,lastSuccessAt,errorCode,nextRetryAt,pending}`. 토큰과 내부 경로는 제외한다 |
| API-015 | `GET /projects/{id}/overview` | REQ-003 | 없음 | `{snapshotId,repositoryObservedAt,projectObservedAt,projectAccess,counts,progress,recentChanges,tasks,partial}`. 조건은 [API-015](#api-015-현황-집계) |
| API-016 | `GET /projects/{id}/tasks` | REQ-003 | cursor, status, assignee | 확정 작업 전체 목록과 작업마다의 Issue 연결 상태·담당자. API-015의 `tasks` 첫 20개를 잇는 계약이다 |
| API-017 | `GET /projects/{id}/documents` | REQ-004 | snapshotId 선택, cursor | `{snapshotId,items:[{id,path,title,kind}],nextCursor}` |
| API-018 | `GET /projects/{id}/documents/{documentId}` | REQ-004, REQ-005 | snapshotId 선택 | `{id,snapshotId,sourceRevision,title,html,headings,diagrams,links,warnings}`. 조건은 [API-018](#api-018-문서-본문) |
| API-019 | `GET /projects/{id}/search` | REQ-004 | q 1~200자, cursor, snapshotId 선택 | 문서 제목·본문에서 찾은 `{documentId,title,excerpt,anchor}`. HTML snippet은 escape 처리한다 |
| API-020 | `GET /projects/{id}/assets/{assetId}` | REQ-004, REQ-007 | snapshotId 필수 | 권한 확인 후 이미지 bytes. 허용된 MIME과 nosniff를 적용한다 |
| API-021 | `POST /webhooks/github` | REQ-006 | GitHub delivery·event·signature 헤더와 raw body | 202. 조건은 [API-021](#api-021-github-webhook) |
| API-022 | `GET /health/live` | 없음. 배포 확인용이며 근거 문서인 `tech-ops`가 보류다 | 없음 | 200 프로세스 생존 여부만. 의존성은 확인하지 않는다 |
| API-023 | `GET /health/ready` | 없음. 배포 확인용이며 근거 문서인 `tech-ops`가 보류다 | 없음 | 200 DB 등 필수 의존성의 준비 여부만 |
| API-024 | `GET /github/repositories/{githubRepositoryId}/branches` | REQ-002, REQ-007 | 없음 | 앱과 사용자 모두 접근 가능한 저장소의 브랜치 `{items:[{name,isDefault}]}`. 볼 수 없으면 404 |

API-024는 2026-09-11에 추가했다. [UI-000](../02-ui-spec/ui-screens.md)의 브랜치 스위처와 [UI-001](../02-ui-spec/ui-screens.md)의 연결 양식이 브랜치를 목록에서 고르는데 그 목록을 주는 계약이 없었다. 저장소 목록(API-008)에 브랜치를 함께 담지 않은 이유는 목록을 열 때마다 저장소 수만큼 GitHub를 더 부르게 되기 때문이다.

`연결 요구`가 `없음`인 계약은 MVP 요구에서 나오지 않은 계약이다. 근거 문서가 생기면 그때 채운다. 비워 두면 빠뜨린 것과 구분할 수 없어 사유를 적는다.

반대 방향도 하나 비어 있다. REQ-001 ~ REQ-007은 위 계약이 덮지만 **REQ-008 공통 작성 규칙에는 아직 계약이 없다.** 산출물 체크리스트 조회 계약은 [구현계획](../04-tasks/implementation-plan.md) TASK-001 이후에 추가한다. 지금 그 계약을 미리 적으면 구현하지 않을 계약이 확정 문서에 남는다.

## API-002 로그인 콜백

**접근 조건:** 세션 없이 부른다. `state`는 로그인 시작이 심은 쿠키의 값과 같아야 하며 한 번만 쓴다. 콜백이 그 쿠키를 지우므로 같은 `state`로 다시 부르면 실패한다.

**출력:** 허용 목록을 통과하면 세션을 회전하고 `returnTo`가 가리키는 서비스 내부 경로로 302한다. `returnTo`가 없거나 외부 주소면 `/`로 보낸다.

**오류:** `state` 불일치·만료, GitHub 토큰 교환 실패, 신원 조회 실패는 모두 세션을 발급하지 않고 `/login?error=state`로 302한다. 실패 원인을 응답으로 구분해 알려주지 않는다.

허용 목록에 없는 계정은 GitHub 인증에 성공해도 **세션을 발급하지 않고** `/uninvited`로 302한다. 그 화면([UI-006](../02-ui-spec/ui-screens.md))은 프로젝트의 존재 여부를 알려주지 않는다. 세션이 없으므로 그 화면에서 부른 API-003은 401이다.

## API-009 프로젝트 연결

**입력:** `{githubRepositoryId,branch,docsRoot,githubProjectNodeId?}`. 등록자 정보는 본문이 아니라 세션에서 가져온다.

**접근 조건:** 앱 접근, 실제 존재하는 브랜치, 실제 존재하는 경로, 선택한 GitHub Project를 모두 확인한다. Project를 등록할 권한과 그 Project를 프로젝트의 모든 사용자에게 보여줄 권한은 다르다. 후자를 확인할 수 없으면 등록은 받되 조회 결과를 다른 사용자와 공유하지 않는다.

**오류:** `docsRoot`는 저장소 상대 POSIX 경로여야 한다. `..`, 절대경로, symlink escape는 422다. 같은 저장소가 이미 연결되어 있으면 409와 함께 이미 열람할 수 있는 프로젝트 `id`를 준다.

**부작용:** 프로젝트를 만들고 첫 동기화를 예약한다.

## API-015 현황 집계

**출력:** `counts`와 `progress`는 모든 페이지 수집이 끝난 범위만 계산한다. `tasks`는 첫 20개이고 전체 목록은 API-016이다. `partial`은 어느 원천을 못 읽어 집계가 불완전한지를 알린다.

**접근 조건:** GitHub Project 정보는 요청한 사용자의 자격증명으로 조회하며, 한 사용자의 Project 조회 결과를 다른 사용자에게 공유하지 않는다.

**오류:** Project를 조회할 수 없으면 실패로 만들지 않고 `projectAccess:"unavailable"`과 Project 기반 `progress:null`로 답한다. 문서와 Issue 정보는 접근 가능한 범위에서 유지한다.

## API-018 문서 본문

**출력:** `html`은 서버 allowlist 정화 결과다. `headings`는 `{level,id,text}`, `diagrams`는 `{id,syntax:"mermaid",source}`이며 `html`의 서비스 생성 placeholder와 연결한다. 원본 Markdown의 script를 `html`로 보내지 않는다. React는 diagram source를 코드로 평가하지 않고 제한된 Mermaid에만 전달한다.

**오류:** 첫 동기화 전이면 409 `DOCUMENTS_NOT_READY`다. 회수된 snapshot을 지정하면 410이며 최신 목록으로 돌아갈 수 있게 안내한다. 응답 시 추가 검증에 실패한 문서는 `html` 대신 422를 반환한다.

**접근 조건:** 이전 snapshot을 지정한 URL에서도 현재 사용자의 권한을 다시 확인한다.

## API-021 GitHub webhook

**입력:** GitHub의 delivery·event·signature 헤더와 raw body다.

**접근 조건:** raw body로 HMAC 서명을 검사한다. 세션은 요구하지 않는다.

**출력:** 허용한 이벤트만 동기화로 예약한다. 정상과 중복 모두 202다.

**오류:** 서명이 맞지 않으면 401, 본문이 너무 크면 413이다.

**부작용:** 동기화 작업을 예약한다. GitHub에는 쓰지 않는다.

## 문서 수집과 snapshot

API-017 ~ API-020에 함께 적용된다.

- 첫 동기화 전 목록(API-017)은 빈 `items`와 `snapshotId:null`이다. 목록은 비어도 오류가 아니고, 본문 조회(API-018)만 409다.
- 링크는 파싱한 뒤 같은 snapshot의 문서·첨부 대상으로 변환한다. 응답에 담는 URL은 서비스가 만든 경로만 쓴다.
- 실패한 snapshot의 문서를 정상 문서 목록에 섞지 않는다. 이전 정상 snapshot이 있으면 실패한 갱신 대신 그것을 유지한다.
- 최초 수집이 실패하면 빈 문서 목록과 sync 오류(API-014)를 함께 보여준다. 파일별 검증 오류는 sync 진단에 보관한다.
- 서로 다른 revision의 문서를 한 snapshot에 섞어 게시하지 않는다.
