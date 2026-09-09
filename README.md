# SyncDoc

SDD 방식으로 작성한 명세와 GitHub 협업 현황을 연결하는 프로젝트 관리 서비스.

## 목표

- AGENTS.md와 공통 Markdown 규칙으로 프로젝트마다 같은 기준의 Spec 작성
- 일반 Markdown 링크와 안정적인 ID로 원문 중복 최소화
- 사람이 읽기 쉬운 명세 화면
- GitHub Issues·Projects·PR을 연결한 담당 작업과 진행 현황 대시보드
- 개인·팀이 클라우드 또는 사내 서버에서 사용하는 호스팅형 서비스

현재는 요구·규칙 설계 단계이며 애플리케이션은 아직 구현하지 않았다.

## 문서

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

확정 규칙은 rules/, 검토 중 명세는 docs/에 둔다. UI 읽기 방식, 기술 스택, GitHub Project, 인증과 배포 환경은 후속 설계 대상이다. 실행 가능한 빌드·테스트 명령은 아직 없다.

2026-09-08 전면 재시작 후 작성한 자료만 Git으로 관리한다. 이전 작업 보관본은 게시 대상에서 제외한다.
