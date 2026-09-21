---
id: DOC-020
type: tech-ops
status: 확정
---

# 실행과 운영

SyncDoc을 어떻게 띄우고, 무엇으로 상태를 확인하고, 무엇이 깨졌을 때 어떻게 되돌리는지 적는다. 2026-09-21 기준으로 **외부 서버를 고르지 않았다.** 아래는 로컬 컨테이너 구성이며, 여기서 확인한 것과 실제 운영에서 확인해야 할 것을 구분해 적는다.

## 실행 환경

| 항목 | 값 |
|---|---|
| 런타임 | Java 25 (Temurin), Spring Boot 4.1.1 |
| 데이터베이스 | PostgreSQL 17 |
| 웹 | 정적 자산 + nginx 1.27. 브라우저는 웹 컨테이너 한 곳만 본다 |
| 프로세스 | API와 수집 worker가 한 프로세스다. `SYNCDOC_SYNC_WORKER_ENABLED=false`로 API만 띄울 수 있다 |
| 기본 포트 | 웹 8081, 백엔드 8080, DB 5433 |

컨테이너 정의는 [`deploy/compose.yaml`](../../deploy/compose.yaml), 이미지는 `backend/Dockerfile`과 `frontend/Dockerfile`이다. 두 이미지 모두 빌드와 실행 단계를 나눠 실행 이미지에 빌드 도구와 소스를 남기지 않는다. 백엔드는 root가 아닌 사용자로 돈다.

```bash
cp deploy/.env.example deploy/.env   # 값 채우기
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d --build
```

개발 중에는 컨테이너 대신 `./gradlew bootRun`과 `npm run dev`를 쓴다. 그 방식은 [README](../../README.md)에 있다.

## 설정·비밀값 주입

값은 모두 환경변수로 넣는다. 파일에 적어 이미지에 굽지 않는다. 목록과 설명은 [`deploy/.env.example`](../../deploy/.env.example)이 정본이다.

| 값 | 없으면 |
|---|---|
| `SYNCDOC_GITHUB_CLIENT_ID`·`CLIENT_SECRET` | 로그인이 동작하지 않는다 |
| `SYNCDOC_GITHUB_APP_ID`·`PRIVATE_KEY` | 로그인·연결은 되지만 수집이 `INSTALLATION_TOKEN_UNAVAILABLE`로 실패한다 |
| `SYNCDOC_GITHUB_WEBHOOK_SECRET` | webhook을 받지 않고 주기 조회로만 갱신한다 |
| `SYNCDOC_TOKEN_KEY` | 사용자 토큰을 암호화할 수 없어 기동에 실패한다 |
| `SYNCDOC_CSRF_KEY` | 상태를 바꾸는 요청을 검사할 수 없다 |
| `SYNCDOC_ADMIN_GITHUB_USER_ID` | 최초 관리자가 없어 아무도 초대할 수 없다 |

지켜야 할 것은 세 가지다. **토큰 암호화 키는 DB 밖에 둔다** — 한 곳이 새도 다른 한 곳이 남는다. **private key는 줄바꿈을 `\n`으로 바꿔 한 줄로 넣고 따옴표로 감싼다** — 값에 공백이 있어 따옴표가 없으면 셸이 끊는다. **평문 http로 도는 로컬 구성에서만 `SYNCDOC_COOKIE_SECURE=false`를 쓴다** — 실제 배포에서는 켠다.

`deploy/.env`는 Git에 올리지 않는다(`.gitignore`). 실제 배포에서는 호스팅의 비밀값 저장소를 쓰고 이 파일을 복사하지 않는다.

## 배포

로컬 컨테이너 구성까지가 지금 범위다(2026-09-21 사용자 확정). 순서는 이렇다.

1. 이미지를 만든다. 백엔드 이미지는 테스트를 돌리지 않는다 — 테스트는 CI가 돌린다.
2. `postgres`가 건강해진 뒤 `backend`가 뜨고, `backend`가 건강해진 뒤 `web`이 뜬다. 순서는 compose의 `depends_on`이 지킨다.
3. 스키마는 애플리케이션이 기동할 때 Flyway가 올린다. 별도 명령이 없다.
4. 롤백은 이전 이미지로 다시 띄우는 것이다. **migration은 되돌리지 않는다** — 새 컬럼을 쓰지 않는 이전 버전은 그대로 돈다.

OAuth 콜백 주소는 GitHub App에 등록한 값과 정확히 같아야 한다. 컨테이너 구성(`http://localhost:8081/...`)과 개발 서버(`http://localhost:5173/...`)는 다른 주소이므로 쓰려면 둘 다 등록한다.

외부 호스팅·도메인·인증서·이미지 저장소는 아직 고르지 않았다. 고른 뒤 이 절에 실제 배포 명령과 롤백 절차를 적는다.

## 상태 확인

| 확인 | 방법 |
|---|---|
| 프로세스 생존 | `GET /api/v1/health/live` (API-022) |
| 의존성 준비 | `GET /api/v1/health/ready` (API-023). 컨테이너 HEALTHCHECK가 이 값을 본다 |
| 수집 상태 | `GET /api/v1/projects/{id}/sync` (API-014). 마지막 시도·마지막 성공·다음 재시도·오류 코드 |
| 집계가 확정인지 | `GET /api/v1/projects/{id}/overview`의 `partial` |

두 값을 섞지 않는다. **프로세스가 살아 있다는 것과 수집이 돌고 있다는 것은 다르다.** 수집이 며칠 멈춰도 `health/live`는 200이다. 수집이 멈춘 것은 화면의 동기화 칩과 API-014가 알린다.

로그에는 토큰·client secret·암호화 키·App JWT가 들어가지 않는다. 진단(`sync_runs.diagnostics_json`)에는 저장소 안의 경로와 오류 코드만 담는다.

## 장애·복구

| 상황 | 서비스 동작 | 사람이 할 일 |
|---|---|---|
| GitHub 중단·429 | 수집이 실패로 기록되고 60초~15분 backoff로 재시도한다. 마지막 정상 게시본은 그대로 보인다 | 없다. 길어지면 화면의 실패 배너와 오류 코드를 본다 |
| 설치 토큰 발급 실패 | `INSTALLATION_TOKEN_UNAVAILABLE`로 기록되고 문서는 마지막 게시본이 보인다 | App private key와 설치 권한을 확인한다 |
| Issue 권한 없음 | 문서 현황은 그대로 보이고 집계만 `partial`로 표시된다 | App 권한에 Issues·Pull requests read를 더한다 |
| worker·서버 재시작 | 임대가 끊긴 작업을 다른 worker가 회수해 이어 한다. 오래된 worker가 나중에 돌아와도 결과를 남기지 못한다 | 없다 |
| DB 재시작 | 기동 시 Flyway가 스키마를 확인하고 이어 받는다. 게시본과 작업 큐는 DB에 있으므로 유실되지 않는다 | 없다 |
| 수집이 계속 실패 | 마지막 정상 게시본을 계속 보여준다. 빈 목록으로 바뀌지 않는다 | API-014의 오류 코드로 원인을 가른다 |

원칙은 하나다. **실패한 수집이 마지막 정상 데이터를 덮어쓰지 않는다.** 게시본은 문서를 모두 모은 뒤에만 전환되고, 전환은 임대 확인과 같은 트랜잭션에서 일어난다.

## 백업 대상

다시 만들 수 없는 것만 백업한다.

| 대상 | 이유 |
|---|---|
| PostgreSQL 전체 (`syncdoc-postgres` 볼륨) | 초대·세션·연결 설정은 서비스 고유 정본이다. 잃으면 누가 무엇을 연결했는지 복원할 수 없다 |
| `SYNCDOC_TOKEN_KEY`와 그 버전 | DB 밖에 따로 보관한다. 키가 없으면 백업을 복원해도 저장된 토큰을 풀 수 없다 |
| GitHub App private key·client secret | 잃으면 재발급해야 하고 설치 승인도 다시 받는다 |

게시본·문서·첨부·Issue 사본은 **백업하지 않아도 된다.** GitHub 원문에서 다시 만들 수 있는 파생 데이터이며, 복원 후 첫 수집이 다시 채운다. 다만 복원 직후에는 문서가 비어 있으므로 화면이 첫 수집 대기로 보인다.

복원 절차는 볼륨을 되돌리고 같은 `SYNCDOC_TOKEN_KEY`로 띄우는 것이다. 키가 다르면 저장된 토큰을 풀 수 없어 사용자가 다시 로그인해야 한다.

## 아직 확인하지 않은 것

- 외부 서버에서의 실제 배포와 롤백. 지금 기록은 로컬 컨테이너 구성에서 확인한 것이다.
- 백업 복원을 실제로 한 결과. 절차만 적었고 돌려 보지 않았다.
- webhook 실제 delivery. 공개 URL이 필요하며 서명 검증은 테스트로만 확인했다.
- 부하와 자원 사용량. 문서 수·첨부 크기에 따른 메모리·시간은 재지 않았다.
