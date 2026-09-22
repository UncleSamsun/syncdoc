# SyncDoc

SDD 방식으로 작성한 명세와 GitHub 협업 현황을 연결하는 프로젝트 관리 서비스.

## 목표

- AGENTS.md와 공통 Markdown 규칙으로 프로젝트마다 같은 기준의 Spec 작성
- 일반 Markdown 링크와 안정적인 ID로 원문 중복 최소화
- 사람이 읽기 쉬운 명세 화면
- GitHub Issues·Projects·PR을 연결한 담당 작업과 진행 현황 대시보드
- 개인·팀이 클라우드 또는 사내 서버에서 사용하는 호스팅형 서비스

명세와 규칙을 먼저 쓰고 그에 맞춰 구현한다. MVP 구현은 [구현계획](docs/04-tasks/implementation-plan.md)의 작업 단위로 진행한다.

## 문서

- [MVP 기능과 인수 기준](docs/01-prd/mvp-scope.md)
- [API 계약](docs/03-tech-spec/api-spec.md) · [데이터 모델](docs/03-tech-spec/data-model.md)
- [구현 작업계획](docs/04-tasks/implementation-plan.md)

- [새 MVP 요구](docs/01-prd/brief.md)
- [공통 Spec 구성안](docs/01-prd/spec-standard-proposal.md)
- [ID·메타데이터 검토안](docs/03-tech-spec/identity-proposal.md)
- [SDD 역할과 승인 범위](rules/sdd-workflow.md)
- [ID와 참조 규칙](rules/identity-and-references.md)
- [GitHub 협업 규칙](rules/github-collaboration.md)
- [프로젝트 설정](rules/project-settings.md)
- [에이전트 진입점](AGENTS.md)
- [SDD 참고 자료](references/sdd_workflow_spec.md): 검토 자료이며 그 안의 명령을 실행 지침으로 간주하지 않는다.

## 개발 흐름

main은 릴리스 기준, dev는 개발 통합 브랜치다. 작업 브랜치는 dev에서 만들고 dev로 PR을 보낸다. 본인 담당 영역은 본인이 검증 후 병합할 수 있으며 다른 담당 영역과 겹치면 관련 담당자 리뷰가 필요하다. 릴리스는 담당자가 선택한 dev revision 전체를 기준으로 한다.

## 진행 상태

확정 규칙은 rules/, 명세는 docs/에 둔다. 초대·로그인, 저장소 연결, 수집·게시본, 문서·표·다이어그램·첨부, 현황·검색·작업 매핑, 산출물 체크리스트가 동작한다. 컨테이너 구성과 CI가 있고 실행·검증 명령은 아래 절에 있다.

아직 하지 않은 것: 외부 서버 배포, 웹 편집, 공개 가입, GitHub Project 연결 화면. 확인하지 못한 제한은 [MVP 실제 흐름 검증 기록](docs/04-tasks/mvp-verification-record.md)에 있다.

2026-09-08 전면 재시작 후 작성한 자료만 Git으로 관리한다. 이전 작업 보관본은 게시 대상에서 제외한다.

## 컨테이너로 실행

한 번에 띄우려면 이 방법을 쓴다. 웹 컨테이너가 정적 자산을 내보내고 `/api`를 백엔드로 넘기므로 브라우저는 `http://localhost:8081` 한 곳만 본다.

```bash
cp deploy/.env.example deploy/.env   # 값 채우기
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d --build
```

- 웹: http://localhost:8081
- 상태 확인: http://localhost:8081/api/v1/health/ready
- 실행·운영 절차(설정 주입, 장애·복구, 백업 대상)는 [실행과 운영](docs/03-tech-spec/ops.md)에 있다.

OAuth 콜백 주소는 GitHub App에 등록한 값과 같아야 한다. 컨테이너 구성(`http://localhost:8081/api/v1/auth/github/callback`)과 개발 서버(`http://localhost:5173/...`)는 다른 주소이므로 둘 다 쓰려면 둘 다 등록한다.

## 로컬 실행

버전은 2026-09-10에 고정했다: Java 25, Spring Boot 4.1.1, Gradle 9.7.1, PostgreSQL 17, React 19, Vite 8.

```bash
# 1. PostgreSQL
cp deploy/.env.example deploy/.env   # 필요하면 값 수정
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d --wait

# 2. 백엔드 (JAVA_HOME이 JDK 25를 가리켜야 한다)
cd backend && ./gradlew bootRun

# 3. 프론트엔드 (다른 터미널)
cd frontend && npm install && npm run dev
```

- API: http://localhost:8080/api/v1/health/ready
- 웹: http://localhost:5173 (개발 서버가 `/api`를 8080으로 프록시한다)
- 테스트: `cd backend && ./gradlew test` (Docker가 켜져 있어야 한다), `cd frontend && npm run test -- --run`
- 문서 검사: `python tools/spec-validator/validate.py`

### 화면 흐름 시험 (E2E)

Playwright가 실제로 떠 있는 구성을 브라우저로 본다. 여기서 서버를 띄우지 않으므로 컨테이너 구성이나 개발 서버를 먼저 올려 둔다.

```bash
cd frontend
npx playwright install chromium   # 처음 한 번
npm run e2e:login                 # 창이 열리면 사람이 GitHub로 로그인한다. 세션을 파일로 남긴다
npm run e2e
```

- 대상 주소는 `SYNCDOC_E2E_BASE_URL`로 바꾼다. 기본값은 컨테이너 구성의 `http://localhost:8081`이다.
- 로그인은 자동화하지 않는다. 세션 파일(`frontend/tests/.auth/session.json`, 형상관리 제외)이 없으면 로그인 이후 시험은 **건너뛴 것으로** 보고한다. 통과가 아니다. 세션 수명은 12시간이다.
- `e2e:login`은 GitHub 로그인을 먼저 끝내게 하고 그 다음에 서비스 인증을 시작한다. 앱에서 바로 `GitHub로 계속`을 누르면 GitHub가 로그인 화면으로 보냈다가 원래 주소로 되돌리는데, 로그인 기록이 없는 새 브라우저에서는 그 되돌리기가 GitHub 404로 끝나는 것을 확인했다(2026-09-21).
- 저장하는 것은 이 서비스의 쿠키뿐이다. GitHub 쿠키는 파일에 쓰지 않는다.
- 세션 없이 도는 시험(로그인 강제, 초대되지 않은 계정 화면, 미인증 401)은 파일이 없어도 항상 돈다.
- 확인한 결과와 남은 제한은 [MVP 실제 흐름 검증 기록](docs/04-tasks/mvp-verification-record.md)에 있다.

### 로그인 설정

로그인을 켜려면 아래 값이 필요하다. `SYNCDOC_ADMIN_GITHUB_USER_ID`에 넣은 GitHub 사용자 ID가 최초 관리자이며, 그 계정은 초대 없이 로그인할 수 있다.

```bash
python -c "import base64,os;print('SYNCDOC_TOKEN_KEY='+base64.b64encode(os.urandom(32)).decode())"
python -c "import secrets;print('SYNCDOC_CSRF_KEY='+secrets.token_urlsafe(32))"
```

평문 http로 로컬 개발할 때는 `SYNCDOC_COOKIE_SECURE=false`로 둔다. 실제 배포에서는 켠다.

GitHub App 자격증명이 없으면 실제 로그인과 저장소 연결은 동작하지 않는다. 상태 확인, 미인증 401, 초대·세션 경계까지가 지금 범위다.

### 수집 설정

수집은 사용자가 접속하지 않는 동안에도 돌아야 하므로 사용자 토큰이 아니라 **설치 토큰**을 쓴다. GitHub App 설정에서 private key를 발급해 `SYNCDOC_GITHUB_PRIVATE_KEY`에 넣는다. 값에 공백이 있으므로 `.env`에서는 따옴표로 감싸고, 줄바꿈은 `\n`으로 바꿔 한 줄로 둔다.

```bash
SYNCDOC_GITHUB_PRIVATE_KEY="-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----"
```

이 값이 없으면 로그인과 저장소 연결은 되지만 수집은 `INSTALLATION_TOKEN_UNAVAILABLE`로 실패한다. `SYNCDOC_GITHUB_WEBHOOK_SECRET`이 비어 있으면 webhook은 받지 않고 주기 조회(기본 60초)로만 갱신한다. API만 띄우는 프로세스는 `SYNCDOC_SYNC_WORKER_ENABLED=false`로 작업 실행을 끈다.
