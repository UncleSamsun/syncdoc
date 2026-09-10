# SyncDoc 프로젝트 설정

## 저장소

- GitHub: https://github.com/UncleSamsun/syncdoc
- 공개 범위: Public (사용자 지정)
- 릴리스 기준 브랜치: `main`
- 개발 통합 브랜치: `dev`
- 작업 브랜치: `dev`에서 파생, `<type>/<issue-number>-<short-description>`
- 초기 저장소 등록은 현재 문서와 규칙을 main/dev에 동일하게 올린다. 후속 변경은 협업 규칙을 따른다.

## 적용 규칙 버전

[REQ-008](../docs/01-prd/mvp-scope.md)이 요구하는 "프로젝트가 기록하는 적용 규칙 버전"을 이 표 한 곳에서 관리한다. 문서마다 수동으로 복사하지 않는다. 검증기와 산출물 체크리스트는 이 값을 근거로 검사 대상을 정한다.

| 규칙 | 버전 | 상태 |
|---|---|---|
| [GitHub 협업](github-collaboration.md) | 2026-09-09 | 활성 |
| [ID와 문서 참조](identity-and-references.md) | 2026-09-09 | 활성 (ID 발급 형식·메타데이터 스키마 미확정) |
| [SDD 역할과 승인 범위](sdd-workflow.md) | 2026-09-09 | 활성 |
| [공통 UI 규칙](../docs/02-ui-spec/ui-conventions.md) | 2026-09-10 | 활성 |
| [화면 명세](../docs/02-ui-spec/ui-screens.md) | 2026-09-10 | 활성 |
| [문서 작성 규칙](spec-writing.md) | 2026-09-10 | 활성 |
| [검증 규칙](validation.md) | 2026-09-10 | 활성 (검증기 [`tools/spec-validator/`](../tools/spec-validator/README.md) 구현) |

버전은 해당 규칙이 확정된 날짜다. 규칙을 고치면 이 표의 날짜도 함께 올린다. 미작성 규칙을 활성으로 표시하지 않는다.

산출물 목록과 산출물별 필수 내용의 정본은 [문서 작성 규칙](spec-writing.md) §3이다. [공통 Spec 구성안](../docs/01-prd/spec-standard-proposal.md)의 해당 절은 승격 후 링크만 남겼다.

## 적용 Spec

이 프로젝트가 적용하는 문서 종류다. 종류와 필수 내용의 정의는 [문서 작성 규칙](spec-writing.md) §3에 있다. [검증 규칙](validation.md) C1이 이 표를 읽어 필수 문서 존재를 판정하므로 표의 형식을 바꾸지 않는다.

| type | 적용 | 사유 |
|---|---|---|
| prd-overview | 적용 | |
| prd-requirements | 적용 | |
| ui-conventions | 적용 | |
| ui-screens | 적용 | |
| tech-overview | 적용 | |
| tech-interface | 적용 | |
| tech-data | 적용 | |
| tech-ops | 보류 | 실행·운영 설계는 TASK-008에서 작성한다 |
| tasks | 적용 | |
| proposal | 적용 | |
| record | 적용 | |
| guide | 적용 | |

## 현재 상태

### 로컬 실행 환경

| 항목 | 값 |
|---|---|
| JDK 25 | `C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot` (Microsoft OpenJDK 25.0.4.1 LTS, 2026-09-10 설치) |
| 문서 검증기 | `python tools/spec-validator/validate.py` (Python 3.11, 표준 라이브러리만) |

셸의 기본 `java`는 아직 Temurin 17이다. Gradle toolchain에서 JDK 25를 명시해 쓰고 PATH 순서에 의존하지 않는다. 이 확인은 TASK-001의 검증 항목이다.

### 확정 기술 구성

2026-09-09 최신 확정: React + TypeScript, Spring Boot 4 + Java 25, commonmark-java + GFM 표 확장, 브라우저 Mermaid, PostgreSQL, 컨테이너 호스팅. VitePress는 Java 대안 논의 후 제품 렌더러에서 대체했다. 문서별 Node.js 빌드는 사용하지 않으며 React 앱 빌드 환경은 별개다. 상세는 [구현 구조](../docs/03-tech-spec/architecture-proposal.md)를 참조한다. 세부 버전과 ORM은 후속 설계 대상이다.

화면 흐름은 프로젝트 선택 → 현황·문서 목록 → 문서 선택 시 React 문서 화면으로 유지한다. VitePress 비교 화면은 읽기 스타일 참고이며 제품 기능으로 그대로 이식하지 않는다. 추가 목업은 요청하지 않았다.

문서·협업 규칙을 정의하는 단계다. 애플리케이션, 실행 가능한 빌드·테스트·링크 검증기, GitHub Project, 보호 규칙, 배포 파이프라인은 아직 구성하지 않았다. 검증 명령을 임의로 만들거나 검증 통과로 표시하지 않는다.

## 추가로 정할 설정

- 로그인·초대·저장소 접근 방식
- 기준 GitHub Project와 담당 영역
- 의존성 세부 버전·ORM 및 실제 빌드·검증 명령
- 검증기 CI 연결 (`tools/spec-validator/` 실행 워크플로)
- 클라우드/사내 서버 배포 환경과 릴리스 절차

공통 동작은 [GitHub 협업 규칙](github-collaboration.md)을 참조한다. 저장소의 공개 여부와 향후 호스팅 서비스의 공개 가입 여부는 별개다.

## MVP 이용자 범위

로그인·접근 원칙은 사용자 확정이다: GitHub OAuth 로그인, GitHub 열람 권한 준용, 초대 사용자만 이용. 앱 유형과 구현 방식은 [접근 설계](../docs/03-tech-spec/access-proposal.md)에서 검토한다.

2026-09-09 사용자 확정: 본인과 초대한 팀원만 사용하는 서비스로 시작한다. 불특정 사용자의 공개 가입은 MVP에 포함하지 않는다. 개인·팀 프로젝트를 모두 지원하고 클라우드 또는 사내 서버에서 호스팅한다. 구체적인 로그인·초대 수단과 저장소 권한 연동은 다음 설계 대상이다.

과거 보관 폴더 삭제는 사용자가 직접 처리한다. 에이전트는 삭제를 재시도하지 않는다. 실제 삭제 완료 여부는 아직 확인하지 않았다.
