# 새 프로젝트 산출물 템플릿

먼저 [작성 하네스](../rules/project-harness.md)를 읽고 대상 프로젝트의 적용 Spec을 결정한다. 아래 파일은 작성 시작점이며 SyncDoc의 활성 명세가 아니다. `DOC-NNN`·`REQ-NNN`·`TASK-NNN` 등은 대상에서 새로 발급하고, `{{...}}`는 실제 내용으로 바꾼다. 초안 문서를 검사 통과만으로 확정하지 않는다.

| template | type |
|---|---|
| [프로젝트 개요](specs/01-prd/overview.md) | prd-overview |
| [기능·품질 요구](specs/01-prd/requirements.md) | prd-requirements |
| [공통 UI 규칙](specs/02-ui-spec/ui-conventions.md) | ui-conventions |
| [화면 명세](specs/02-ui-spec/ui-screens.md) | ui-screens |
| [기술 개요](specs/03-tech-spec/architecture.md) | tech-overview |
| [인터페이스 계약](specs/03-tech-spec/api-spec.md) | tech-interface |
| [데이터 설계](specs/03-tech-spec/data-model.md) | tech-data |
| [실행·운영](specs/03-tech-spec/ops.md) | tech-ops |
| [작업 정의](specs/04-tasks/implementation-plan.md) | tasks |

필수 내용과 라벨의 정본은 [spec-format.json](../rules/spec-format.json)이다. 정의를 바꾸면 템플릿도 함께 점검한다. 파일명/폴더는 시작 예시이며 실제 프로젝트의 링크·규칙·ID를 유지해 적용한다. UI/API가 없는 프로젝트에는 해당 문서를 억지로 추가하지 않는다. 기존 프로젝트에 적용할 때는 문서를 이동하거나 과거 계획을 자동 이관하지 않는다.
