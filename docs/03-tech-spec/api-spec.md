---
id: DOC-010
type: tech-interface
status: 확정
---

# MVP API 계약

아직 서버가 없다. 이 계약은 구현 대상이며 동작하는 엔드포인트가 아니다.

근거: [MVP 기능](../01-prd/mvp-scope.md). 경로 접두어는 `/api/v1`. 공개 객체 식별자는 UUID, GitHub ID는 별도 보존한다. 숫자 GitHub ID는 JSON에서 문자열로 전달한다. 시간은 UTC ISO 8601, 목록 기본 20/최대100, cursor는 서버가 발급한 불투명 문자열이다.

## 공통 인증과 오류

동일 origin의 HttpOnly/Secure/SameSite=Lax 세션 쿠키를 사용한다. 변경 요청은 CSRF 토큰을 검사한다. 사용자 허용 목록과 GitHub 리소스 권한을 서버에서 검사한다. 로그인 외 공개 엔드포인트는 최소 상태 확인과 서명 검증 webhook뿐이다. HTML/검색 결과는 `Cache-Control: private, no-store`로 제공한다. 사용자 GitHub 토큰·설치 토큰·원시 OAuth 응답은 반환하지 않는다.

오류 형식: `{ "code":"RESOURCE_NOT_FOUND", "message":"대상을 찾을 수 없습니다.", "requestId":"...", "details":{} }`. 권한 없는 비공개 대상과 없는 대상은 같은 404. 미로그인 401, 초대/관리 작업 거절 403, 충돌409, 입력422, 제한429(`Retry-After`), GitHub 확인 불가503. details에는 비공개 이름·토큰을 넣지 않는다.

## 인증·초대

| 메서드·경로 | 입력 | 응답·조건 |
|---|---|---|
| GET /auth/github/start | returnTo: 허용된 서비스 내부 경로만 | 302 GitHub 로그인, 일회용 state와 지원되는 PKCE 사용 |
| GET /auth/github/callback | code, state | 일치/만료 검사·허용 목록 확인 후 세션 회전과 302. 실패 시 세션 미발급 |
| GET /me | 없음 | 200 `{id,githubUserId,login,serviceAdmin,csrfToken}` |
| POST /logout | CSRF | 204 세션 무효화 |
| GET /invitations | 관리자 | 허용 목록, GitHub ID와 상태 |
| POST /invitations | `{githubLogin}` | 관리자만. GitHub에서 ID 확인 후 허용 목록 등록. 201 또는 동일 계정 기존 항목200. 이메일 전송 없음 |
| DELETE /invitations/{id} | 관리자 | 204 허용 취소와 사용자 세션 무효화. 최초 관리자 취소 불가409 |

## 저장소·프로젝트

| 메서드·경로 | 입력 | 응답·조건 |
|---|---|---|
| GET /github/repositories | cursor | 앱·사용자 접근 교집합의 후보 `{githubRepositoryId,fullName,private,defaultBranch}` |
| POST /projects | `{githubRepositoryId,branch,docsRoot,githubProjectNodeId?}` | 201 `{id,syncState:"queued"}`. 등록자 정보는 세션에서. 앱 접근·실제 브랜치·경로·선택 Project 확인. 중복409+이미 열람 가능한 id |
| GET /projects | cursor | 허용 프로젝트 목록과 최소 sync 상태. 권한 확인 실패 항목을 비인가에게 노출하지 않고 결과 incomplete 여부 표시 |
| GET /projects/{id} | 없음 | 연결 설정, 현재 snapshotId, sync 상태·시각. 권한 없음404 |
| PATCH /projects/{id} | `{branch?,docsRoot?,githubProjectNodeId?,expectedVersion}` | 연결자/관리자만, 현재 GitHub 접근도 필요. 200 version. 동기화 예약, 버전 충돌409 |
| POST /projects/{id}/sync | 없음 | 연결자/관리자만. 202 `{jobId,reused}`. 활성 작업이 있으면 합침 |
| GET /projects/{id}/sync | 없음 | `{state,lastAttemptAt,lastSuccessAt,errorCode,nextRetryAt,pending}`. 토큰/내부 경로 제외 |

관리 대상 임의 URL은 받지 않는다. docsRoot는 저장소 상대 POSIX 경로이며 `..`, 절대경로, symlink escape는 허용하지 않는다. GitHub Project를 등록할 권한과 모든 사용자에게 그 Project를 보여줄 권한은 다르다.

## 현황·문서

| 메서드·경로 | 입력 | 응답 |
|---|---|---|
| GET /projects/{id}/overview | 없음 | `{snapshotId,repositoryObservedAt,projectObservedAt,projectAccess,counts,progress,recentChanges,tasks,partial}` |
| GET /projects/{id}/documents | snapshotId 선택,cursor | `{snapshotId,items:[{id,path,title,kind}],nextCursor}` |
| GET /projects/{id}/documents/{documentId} | snapshotId 선택 | `{id,snapshotId,sourceRevision,title,html,headings,diagrams,links,warnings}` |
| GET /projects/{id}/search | q 1~200자,cursor,snapshotId 선택 | 문서 제목·본문의 `{documentId,title,excerpt,anchor}`. HTML snippet은 escape 처리 |
| GET /projects/{id}/assets/{assetId} | snapshotId 필수 | 권한 확인 후 이미지 bytes. 허용된 MIME과 nosniff 적용 |
| POST /webhooks/github | GitHub delivery/event/signature headers | raw body HMAC 검사. 허용 이벤트만 예약. 정상/중복202, 잘못된 서명401, 너무 큰 본문413 |

overview.tasks는 첫 페이지20개다. 전체 목록은 `GET /projects/{id}/tasks?cursor=&status=&assignee=`로 제공한다. counts/progress는 모든 페이지 수집이 완료된 범위만 계산한다. Project 정보는 요청 사용자 자격증명으로 조회하며 다른 사용자의 Project 결과를 공유하지 않는다. 조회 불가 시 `projectAccess:"unavailable"`, Project 기반 progress=null, 문서·Issue 정보는 해당 접근 가능 범위에서 유지한다.

문서 html은 서버 allowlist 정화 결과다. headings는 `{level,id,text}`, diagrams는 `{id,syntax:"mermaid",source}`이며 html의 서비스 생성 placeholder와 연결한다. raw Markdown script를 html로 보내지 않는다. React는 diagram source를 코드로 평가하지 않고 제한된 Mermaid에 전달한다.

링크는 파싱 후 같은 snapshot의 문서/첨부 대상으로 변환한다. API 응답 URL은 서비스가 만든 경로만 사용한다. 이전 snapshot을 지정한 URL에서도 현재 사용자 권한을 재확인한다. 회수된 snapshot은410이며 최신 목록으로 돌아갈 수 있게 안내한다.

첫 동기화 전 목록은 빈 items+snapshotId=null, 문서 조회는409 `DOCUMENTS_NOT_READY`. 검증 실패한 수집의 파일별 오류는 sync 진단에 보관한다. 최초 수집이 실패하면 빈 문서 목록과 sync 오류를 보여준다. 이전 정상 snapshot이 있으면 실패한 갱신 대신 그것을 유지한다. 응답 시 추가 검증에 실패한 문서는 html 대신422 오류를 반환한다. 실패한 snapshot을 정상 문서 목록에 섞지 않는다.

## 운영 경로

`GET /health/live`는 프로세스 생존만, `/health/ready`는 DB 등 필수 의존성 준비 여부만 반환한다. 상세 상태·관리 actuator는 외부 공개하지 않는다. 읽기 API가 GitHub에 상태를 쓰지 않는다. Issue 발행은 승인된 에이전트의 GitHub 협업 작업이며 서비스 API로 구현하지 않는다.
