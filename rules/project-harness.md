# 프로젝트 작성 하네스

새 프로젝트의 사람과 에이전트가 무엇을 어디에 작성하고 검토할지 찾는 진입점이다. 산출물 정의를 이 파일에 다시 복사하지 않는다.

## 정본과 역할

| 기준 | 정본 | 담당하는 것 |
|---|---|---|
| 제품 목표 | 프로젝트의 `docs/01-prd/overview.md` | 무엇을 왜 만드는지와 범위 |
| 산출물 종류·필수 내용 | [`spec-format.json`](spec-format.json) | 사람이 쓰는 문서와 검사기가 읽는 기계 기준 |
| 작성·제목·강조 | [`spec-writing.md`](spec-writing.md) | frontmatter·항목·문서 계층·표기 |
| 프로젝트별 적용 | 프로젝트의 `rules/project-settings.md` | 어느 종류를 적용/미적용/보류하는지, 이유와 규칙 버전 |
| ID·참조 | [`identity-and-references.md`](identity-and-references.md) | 이동에도 유지되는 정체성과 링크 |
| 승인·구현 | [`sdd-workflow.md`](sdd-workflow.md) | 사람이 결정하는 범위와 에이전트의 작업 |
| 협업 현황 | [`github-collaboration.md`](github-collaboration.md) | Issue/Project/PR의 상태·책임 |
| 검사 | [`validation.md`](validation.md) | C0/C1/C2의 범위와 한계 |
| 화면 표현 | [공통 UI 규칙 (DOC-005)](../docs/02-ui-spec/ui-conventions.md) | 모든 저장소에 공통인 서체·제목·여백·강조 |

SyncDoc은 기준을 제공하고 적용 상태·문서·협업 현황을 연결하는 하네스다. 제품 목표나 프로젝트별 결정을 대신 만들지 않는다. 기준의 저작·변경은 Git에서 리뷰한다.

## 새 프로젝트에 적용하는 순서

1. 적용할 SyncDoc 규칙의 commit을 고정한다. 최신 브랜치를 매번 따라가며 기준을 조용히 바꾸지 않는다.
2. `spec-format.json`·작성·참조·검증·SDD·협업 규칙을 대상의 `rules/`에 준비한다. 대상에 기존 협업 규칙이 있으면 우선순위·차이를 먼저 합의한다.
3. `project-settings.md`는 대상 프로젝트의 값으로 새로 쓴다. SyncDoc 저장소 이름·계정·GitHub App·서버 주소·로컬 JDK 경로 등 프로젝트 전용 설정은 복사하지 않는다. 적용 Spec 표의 형식은 유지하고 각 종류의 적용 여부와 사유를 결정한다.
4. 대상 `AGENTS.md`를 얇은 진입점으로 만들고 제품 개요와 위 규칙에 연결한다. 실제 ID는 대상 안에서 새로 발급한다. 기존 SyncDoc 요구·작업 번호와 내용을 가져오지 않는다.
5. [산출물 템플릿](../templates/README.md)을 출발점으로 사용한다. 목표 → 요구 → UI → 기술 → 작업의 근거가 이어지게 작성하고 초안/검토/확정을 구분한다. UI 없는 프로젝트는 UI를 억지로 만들지 않는다.
6. 고정한 검사기를 사용한다. 사본을 늘리기보다 `python <고정된-syncdoc>/tools/spec-validator/validate.py <대상-root>`로 검사하고 대상 CI도 같은 commit을 사용하게 한다.
7. 사람의 내용·링크·인수 기준 검토를 마친 후 기준으로 확정하고 구현 Issue/PR을 진행한다. SyncDoc에서 해당 저장소/문서 경로를 연결해 폴더별 목록과 체크리스트를 확인한다.

## 작성과 갱신의 반복

요구 변경 → 해당 정본 수정 → 영향받는 UI/기술/작업 링크 확인 → 검사와 사람 검토 → 승인된 구현 → Issue/PR 증거 → SyncDoc 수집 순서다. 동일한 요구를 작업 문서에 복사해 둘 다 수정하는 흐름은 만들지 않는다. 파일명에 `proposal`이 있어도 현재 기준 여부는 frontmatter의 type/status와 승인 revision으로 판단한다.

템플릿과 가이드는 구현 기준을 대신하지 않는다. 실제 프로젝트의 `status: 확정` 명세가 기준이다. 체크리스트 통과는 내용의 품질이나 사실·승인 여부를 자동 보증하지 않는다. 빈 줄의 개수·헤더 크기를 문서마다 꾸미지 않고 의미 있는 Markdown 계층과 공통 렌더 규칙을 사용한다.

## 현재 가능한 것과 후속 검토

SyncDoc 자체의 실제 작성·재개·검사·출처 확인은 [실전 적용 가이드](../docs/03-tech-spec/harness-dogfood-guide.md)를 따른다. `python tools/harness/dogfood.py . --task TASK-020`은 기존 규칙/검사기를 재사용하는 읽기 점검이며 설치나 자동 실행 도구가 아니다. 서버 컨텍스트 pin 대조와 오프라인 후보/working-tree 검사를 구분한다.

현재는 규칙 파일·검사·GitHub 수집·폴더별 문서 탐색을 연결한다. 규칙의 조직 전체 자동 업데이트, 프로젝트 생성 자동화, 에이전트 실행/승인을 자동 통제하는 기능은 포함하지 않는다. 이를 원하면 별도의 요구·승인·설계가 필요하다. 규칙 버전 lock 파일과 설치 명령의 자동화, 내용 품질 검사 확대는 이 과정을 실제 새 프로젝트에 적용한 뒤 검토한다.
