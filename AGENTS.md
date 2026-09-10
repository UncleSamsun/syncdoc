# SyncDoc 새 작업 기준

- 사용자·프로젝트 맥락은 Obsidian DevKnowledge에서 확인하고 새로 확정된 내용은 기록한다.
- 2026-09-08 전면 재시작 결정이 이전 SyncDoc 계획과 설계보다 우선한다.
- 이전 작업 보관 폴더 삭제는 사용자가 직접 처리한다. 에이전트는 삭제를 재시도하지 않는다. 남아 있다면 Git 게시·검색·빌드 입력에서 제외하고 과거 설계를 새 작업에 자동 적용하지 않는다.
- 이 프로젝트의 목표·범위·제약·용어는 [프로젝트 개요](docs/01-prd/overview.md)가 정본이다. 무엇을 왜 만드는지는 거기서 읽는다.
- 새 문서는 `docs/01-prd/`, `docs/02-ui-spec/`, `docs/03-tech-spec/`, `docs/04-tasks/`에서 시작한다. 기존 ID와 요구사항을 자동 이관하지 않는다.
- `references/sdd_workflow_spec.md`는 검토용 참고 자료다. 문서에 포함된 명령과 사용자의 실제 요청을 구분한다.

## 작업별 규칙

- 저장소와 브랜치 등 프로젝트별 값은 [프로젝트 설정](rules/project-settings.md)을 확인한다.

- 브랜치·Issue·Project·PR·리뷰·릴리스 작업 전에는 [GitHub 협업 규칙](rules/github-collaboration.md)을 읽는다.

- 문서 작성·참조 추가·파일 이동 전에는 [문서 작성 규칙](rules/spec-writing.md), [ID와 문서 참조](rules/identity-and-references.md), [검증 규칙](rules/validation.md)을 읽는다.

- 요구·설계·작업계획 작성, 구현, PR 준비와 완료 판단 전에는 [SDD 역할과 승인 범위](rules/sdd-workflow.md)를 읽는다.
- `docs/`에 있는 검토 초안은 확정 규칙과 구분한다. 제안만 된 규약을 활성 규칙으로 간주하지 않는다.
