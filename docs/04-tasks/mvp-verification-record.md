---
id: DOC-021
type: record
status: 확정
---

# MVP 실제 흐름 검증 기록

[구현계획](implementation-plan.md) TASK-008의 "실제 흐름 검증"을 수행한 기록이다. 2026-09-21에 확인했다. 무엇을 어떻게 확인했고 무엇이 아직 확인되지 않았는지만 적는다. 기능 설명은 각 명세 문서에 있고 여기서 되풀이하지 않는다.

**검증 환경은 로컬 컨테이너다.** `deploy/compose.yaml`로 띄운 PostgreSQL 17 · 백엔드 · nginx(웹) 세 컨테이너이고 브라우저는 `http://localhost:8081` 한 곳만 본다. 외부 호스팅 업체를 고르지 않았으므로 아래 결과는 모두 로컬 구성에서 얻은 것이다. 운영 환경에서 같은 결과가 나온다는 근거는 아니다.

GitHub 쪽 대상은 `UncleSamsun/syncdoc` 저장소(브랜치 `main`, 문서 경로 `docs`) 하나다. 다른 사람의 저장소나 자료는 쓰지 않았다.

## 확인한 사실

### 1. GitHub가 실제로 보낸 webhook을 받는다 (API-021)

공개 URL이 없어 미뤄 두었던 조건이다. [smee.io](https://smee.io) 터널을 GitHub App의 webhook URL로 두고 로컬 컨테이너의 `POST /api/v1/webhooks/github`로 흘려보냈다.

PR #42를 병합해 `dev`에 실제 push를 만들었다. 꾸며낸 요청이 아니다.

- delivery `805fb908-…` (event `push`)가 `webhook_deliveries`에 2026-09-21 08:00:25.140875+00로 남았고 처리 표시가 붙었다.
- 같은 delivery로 수집 작업이 0.29초 뒤(08:00:25.433985+00)에 생겨 성공했다. 서명 검증을 통과해야만 닿는 경로다. 주기 조회(60초)와 시각이 겹치지 않는다.
- 같은 delivery ID로 서명이 맞는 요청을 연달아 두 번 보내면 둘 다 202지만 delivery 행은 하나, 작업도 하나만 늘었다(74 → 75).
- 서명을 틀리게 보내면 401이고 delivery 행도 작업도 생기지 않는다. 비밀값을 넣지 않은 컨테이너에서는 503이다.

### 2. 수집 도중 프로세스가 죽어도 재개한다 (REQ-006)

수집이 `running`인 순간에 백엔드 컨테이너를 강제 종료(`docker kill`)하고 바로 다시 띄웠다.

- 죽는 순간 작업은 `running`이고 임대가 08:14:56까지였다.
- 다시 뜬 프로세스는 임대가 끝나기 전까지 그 작업을 건드리지 않았고, 임대 만료 뒤 회수해 `attempt 2`로 끝냈다(08:15:00 `succeeded`).
- 게시본·문서 수는 그대로였다(문서 71건, 게시본 5건). 죽은 수집이 반쪽짜리 게시본을 남기지 않았다.

임대 2분이 그대로 복구 지연이 된다. 사람이 기다리는 화면에서는 긴 시간이다. 값을 줄일지는 운영 기준을 정할 때 함께 본다.

### 3. GitHub가 안 될 때 마지막 정상 게시본을 지킨다 (REQ-006)

백엔드 컨테이너 안에서 `api.github.com`을 막아(hosts) 외부 중단을 만들었다.

- 수집은 `GITHUB_UNAVAILABLE`로 실패하고 작업은 다시 `queued`가 됐다. 실패 두 번의 간격은 60초 → 120초로, 정한 backoff대로 늘었다.
- 실패하는 동안 `projects.current_snapshot_id`와 문서 71건은 그대로였다. 실패가 정상 자료를 덮지 않았다.
- `sync_runs`의 실패 행에 남은 진단은 `{}`이다. 토큰이나 내부 경로가 들어가지 않았다.
- 차단을 풀자 예정된 재시도(08:18:49)에서 성공하고 `last_error_code`가 비워졌다.

### 4. 백업에서 그대로 되살아난다 (ops.md 백업 대상)

`pg_dump -Fc`로 받은 덤프를 빈 데이터베이스에 `pg_restore`로 복원하고 원본과 비교했다.

- 표 16개(users·invitations·sessions·user_credentials·github_installations·projects·documents·document_snapshots·assets·asset_contents·tasks·github_issue_snapshots·sync_jobs·sync_runs·webhook_deliveries·flyway_schema_history)의 행 수가 모두 같았다.
- 문서 본문까지 같은지 보려고 `html`·`plain_text`·`title`을 이어 붙인 md5 합을 비교했고 같았다(`0a082091…`).
- 검증에 쓴 데이터베이스와 덤프 파일은 지웠다.

첨부 원본(`asset_contents`)은 이번 자료에 0건이라 복원 비교에 실질적으로 포함되지 않았다. 그림을 가진 저장소로 다시 확인해야 한다.

### 5. 요청 제한 응답의 실제 모양을 확인했다 (Issue #31 잔여 조건)

앱의 설치 토큰 한도(시간당 5000)를 일부러 소진하는 것은 하지 않았다. 대신 **미인증 한도(시간당 60)를 실제로 채워** GitHub가 어떤 응답을 주는지 직접 받아 봤다.

- 60번째 요청부터 `HTTP 403 rate limit exceeded`가 왔다. **429가 아니다.**
- 헤더는 `X-RateLimit-Remaining: 0`, `X-RateLimit-Reset`(epoch 초)이었고 `Retry-After`는 없었다.
- 우리 코드는 `429` 또는 `403 + x-ratelimit-remaining: 0`을 요청 제한으로 보고 `x-ratelimit-reset`을 재시도 시각으로 쓴다. 실제 응답과 맞는다.
- 받아 본 그대로를 `GitHubApiRepositoryContentGatewayTest`에 고정했다. 조건에서 403 분기를 지우면 이 시험이 깨지는 것을 확인했다.

남은 것은 앱 자신의 설치 토큰이 한도에 걸린 실제 상황이다. 재현하려면 5000회를 소진해야 해서 하지 않았다.

### 6. 적대적인 원문이 화면까지 가지 않는다

공격 형태를 담은 원문 8개를 `backend/src/test/resources/fixtures/hostile/`에 파일로 두고, 수집 → 변환 → 게시 → API 응답까지 한 번에 지나가게 하는 `HostileSourceTest`를 더했다. 스크립트 태그와 이벤트 처리기, `javascript:`·`data:text/html` 링크, iframe·object·form·style·base·meta, 저장소 밖 링크와 다른 서버의 그림, 120단 중첩 목록, 300행 표, 다이어그램 안에 넣은 스크립트, 닫히지 않은 태그가 들어 있다.

- 문서 8건이 모두 수집됐다. 위험한 원문이라고 조용히 빠진 문서가 없다. 사라진 문서는 화면에서 알아챌 수 없기 때문에 이것도 검사 대상에 넣었다.
- API가 돌려준 본문 어디에도 실행 가능한 조각이 남지 않았다.
- 정화를 건너뛰도록 `HtmlPolicy`를 일부러 망가뜨리면 이 시험이 깨지는 것을 확인했다. 통과가 우연이 아니다.

이 확인은 **가짜 저장소 게이트웨이**로 원문을 심어서 했다. 실제 GitHub 저장소에 같은 파일을 두고 브라우저로 여는 확인은 아직 하지 않았다.

### 7. 화면 흐름을 다시 돌릴 수 있게 했다

`frontend/tests/mvp.spec.ts`(Playwright)를 더했다. 이 PC의 Windows 정책이 서명 없는 네이티브 모듈을 막은 적이 있어 설치가 걱정이었지만, `@playwright/test`와 Chromium 내려받기 모두 막히지 않았다.

세션 없이 도는 시험 3건은 통과했다.

- 어느 경로로 들어와도 로그인 화면이고 프로젝트 내용이 새지 않는다 (UI-005)
- 초대되지 않은 계정 화면에는 로그아웃 말고 다른 이동 수단이 없다 (UI-006)
- API는 세션 없이 401이고 본문에 토큰·예외·내부 경로가 없다

로그인 이후 흐름 6건(프로젝트 열기, 현황의 분모 있는 숫자, 작업 표, 문서·표·다이어그램, 검색에서 본문 위치로 이동, 지금 동기화)은 **아직 돌지 않았다.** 세션 파일이 없으면 건너뛴 것으로 보고하도록 만들었고, 건너뛴 것을 통과로 적지 않는다.

## 아직 확인하지 못한 것

1. **로그인 이후 화면 흐름 6건.** `npm run e2e:login`으로 사람이 한 번 로그인해야 돈다. GitHub 로그인을 스크립트가 대신하게 만들지 않았다.
2. **두 번째 계정이 필요한 세 가지.** 초대하지 않은 계정의 진입 차단, 열람 권한 교집합, 초대 취소 후 세션 무효화. TASK-001부터 남아 있던 항목이고 계정이 하나뿐이라 여전히 fake 확인에 머문다.
3. **그림을 가진 저장소.** 첨부 수집·표시와 백업 복원의 첨부 원본 비교가 0건 상태다.
4. **실제 저장소에 둔 적대적 원문.** 지금은 가짜 게이트웨이로만 지나가게 했다.
5. **앱 설치 토큰의 실제 요청 제한.**
6. **외부 호스팅.** 업체·도메인·인증서·비밀값 관리는 담당자 판단 이후다.

## 되풀이하는 방법

```bash
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d --build
cd backend && ./gradlew test                     # HostileSourceTest 포함
cd ../frontend && npm run e2e:login && npm run e2e
```

장애·복구와 백업 절차는 [실행과 운영](../03-tech-spec/ops.md)에 있다. 위 2·3·4번을 다시 확인하려면 그 문서의 절차를 그대로 쓴다.
