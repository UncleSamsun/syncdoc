---
id: DOC-017
type: proposal
status: 검토
---

# 포맷 정의 파일 설계안

산출물 목록과 산출물별 필수 내용을 기계가 읽는 파일 하나로 정의하고, 저장소의 검사기와 SyncDoc이 같은 파일을 읽게 하는 설계다. 2026-09-10 사용자가 방향을 확정했다. 이 문서는 설계 제안이며 규칙 파일·검사기·API·화면을 아직 고치지 않았다.

## 무엇을 제안하는가

[REQ-008](../01-prd/mvp-scope.md)은 기준의 정본을 대상 저장소의 규칙 파일에 두고 SyncDoc은 준수 여부만 보여준다고 확정했다. 그런데 지금 포맷의 정의는 두 곳에 사람 손으로 복제되어 있다. [문서 작성 규칙](../../rules/spec-writing.md) §3·§5의 Markdown 표와 [검사기](../../tools/spec-validator/validate.py)의 상수 `ALLOWED_TYPES`·`REQUIRED_LABELS`·`CONTRACT_COLUMNS`다. SyncDoc의 Java 파서가 여기에 세 번째 사본을 더하면 규칙을 고칠 때마다 세 곳을 맞춰야 하고, 포맷이 다른 저장소는 읽을 수 없다. [공통 Spec 구성안](../01-prd/spec-standard-proposal.md) §7도 "기계가 검사할 필드와 허용 값은 후속 스키마 한 곳에서 정의한다"를 미결로 남겼다.

제안은 세 가지다.

1. 대상 저장소의 `rules/spec-format.json`을 포맷의 기계 정본으로 둔다.
2. Python 검사기는 상수를 버리고 이 파일을 읽는다. 사람이 읽는 §3·§5 표는 남기되, 표와 파일이 어긋나면 검사기가 오류로 잡는다.
3. SyncDoc은 수집 시점의 revision에서 같은 파일을 읽어 검사하고, 결과를 산출물 체크리스트로 보여준다. 기준을 고치는 화면은 만들지 않는다.

### 검토한 대안

| 대안 | 채택 여부 | 이유 |
|---|---|---|
| `rules/spec-format.json` 신설 | 채택 | Python 표준 라이브러리와 Spring Boot가 추가 의존성 없이 읽는다. 파일 하나가 포맷의 정체성이 되어 다른 프로젝트에 고정 버전으로 두기 쉽다 |
| spec-writing.md의 §3·§5 표를 그대로 기계가 읽음 | 기각 | 적용 Spec 표와 계약 일람 표에 선례는 있으나, §5의 검사 단위 열이 산문이라 어휘를 고정해야 하고 규칙 산문과 스키마가 한 파일에 섞여 떼어 옮기기 어렵다 |
| SyncDoc에 기본 포맷 내장, 저장소가 덮어씀 | 기각 | 정본이 둘이 된다. REQ-008의 "규칙 파일이 없으면 미검사" 원칙과 어긋난다 |
| YAML | 기각 | [검증 규칙](../../rules/validation.md) §7의 표준 라이브러리 제약 때문에 Python이 읽을 수 없다 |

## 1. 정의 파일

### 위치

대상 저장소 루트 기준 `rules/spec-format.json` 고정 경로다. [프로젝트 설정](../../rules/project-settings.md)이 `rules/project-settings.md`에 고정된 것과 같은 방식이다. 프로젝트 연결의 `docsRoot` 설정과 무관하게 `rules/`는 저장소 루트에서 찾는다. 파일이 없는 저장소는 미검사다.

### 구조

파일은 `types` 배열 하나를 담는다. 항목은 문서 종류 하나다.

| 필드 | 의미 | 출처 |
|---|---|---|
| `type` | frontmatter `type` 허용 값 | spec-writing §3 `type` 열 |
| `name` | 사람이 읽는 문서 종류 이름 | spec-writing §3 `문서 종류` 열 |
| `spec` | 명세 문서면 `true`, 명세가 아닌 문서면 `false` | spec-writing §3의 아홉 종류·세 종류 구분 |
| `condition` | 적용 조건. 사람이 읽는 문장 | spec-writing §3 `적용 조건` 열 |
| `required` | 필수 내용 이름 목록. 사람이 리뷰에서 확인한다 | spec-writing §3 `필수 내용` 열 |
| `labels` | 허용하는 굵은 라벨 이름 목록. 빈 배열이면 굵은 라벨을 쓰지 않는 종류다 | spec-writing §5 라벨 이름 공간 표 |
| `checks` | 기계 검사 목록. 빈 배열이면 C2를 적용하지 않는다 | spec-writing §5 검사 라벨 표 |

`checks`의 항목은 `unit`으로 구분하는 세 종류뿐이다. 지금 §5 표가 쓰는 검사 단위가 이 셋이고, 새 종류를 추가하려면 [검증 규칙](../../rules/validation.md)을 먼저 고친다.

| `unit` | 다른 필드 | 판정 |
|---|---|---|
| `document` | `labels` | 문서 전체에서 각 라벨이 `**라벨:**` 형태로 있고 같은 줄에 내용이 있는지 |
| `section` | `idPrefix`, `labels` | `## 접두어-NNN` 제목으로 시작하는 섹션마다 각 라벨이 있는지. 섹션이 하나도 없으면 오류 |
| `table` | `heading`, `columns`, `idPrefix` | `## 제목` 아래 첫 표에 지정 열이 있고 모든 행에서 그 칸이 채워졌으며 `ID` 열이 `접두어-NNN` 형식인지 |

정의 파일에는 버전 필드를 두지 않는다. 적용 규칙 버전은 [프로젝트 설정](../../rules/project-settings.md)의 표 한 곳에서 읽는다는 규칙을 그대로 따르고, 그 표에 `포맷 정의` 행을 추가한다. 문서 ID `DOC-NNN`의 형식과 항목 ID의 세 자리 연번은 정의 파일에 넣지 않고 spec-writing §6의 고정 규약으로 둔다. 프로젝트마다 달라질 이유가 아직 없다.

### 현재 포맷을 옮긴 내용

아래가 spec-writing §3·§5를 그대로 옮긴 첫 정의 파일이다. 새 검사는 넣지 않았다.

```json
{
  "types": [
    {
      "type": "prd-overview",
      "name": "프로젝트 개요",
      "spec": true,
      "condition": "모든 프로젝트",
      "required": ["문제", "대상 사용자", "목표", "성공 판단", "포함 범위", "제외 범위", "제약", "적용 Spec", "용어"],
      "labels": ["문제", "대상 사용자", "목표", "성공 판단", "포함 범위", "제외 범위", "제약", "적용 Spec", "용어"],
      "checks": [
        { "unit": "document", "labels": ["문제", "대상 사용자", "목표", "성공 판단", "포함 범위", "제외 범위", "제약", "적용 Spec", "용어"] }
      ]
    },
    {
      "type": "prd-requirements",
      "name": "기능·품질 요구",
      "spec": true,
      "condition": "모든 프로젝트",
      "required": ["요구 ID", "사용자 필요", "기대 동작과 품질 조건", "예외", "인수 기준", "근거"],
      "labels": ["사용자 필요", "기대 동작", "예외", "인수 기준", "근거"],
      "checks": [
        { "unit": "section", "idPrefix": "REQ", "labels": ["예외", "인수 기준", "근거"] }
      ]
    },
    {
      "type": "ui-conventions",
      "name": "공통 UI 규칙",
      "spec": true,
      "condition": "사용자 화면이 있으면",
      "required": ["색·글자·간격 토큰", "공통 컴포넌트", "탐색", "접근성", "공통 상태 표현"],
      "labels": [],
      "checks": []
    },
    {
      "type": "ui-screens",
      "name": "화면 명세",
      "spec": true,
      "condition": "사용자 화면이 있으면",
      "required": ["화면 ID", "연결 요구", "진입·종료 경로", "레이아웃", "사용자 행동", "로딩·빈 상태·오류·권한 상태", "화면 고유 검증"],
      "labels": ["연결 요구", "진입", "종료", "레이아웃", "사용자 행동", "상태", "검증", "관련 작업"],
      "checks": [
        { "unit": "section", "idPrefix": "UI", "labels": ["연결 요구", "검증"] }
      ]
    },
    {
      "type": "tech-overview",
      "name": "기술 개요",
      "spec": true,
      "condition": "모든 프로젝트",
      "required": ["시스템 경계", "모듈 책임·의존성", "외부 연동", "주요 흐름", "기술 선택과 이유", "검증 전략"],
      "labels": [],
      "checks": []
    },
    {
      "type": "tech-interface",
      "name": "인터페이스 계약",
      "spec": true,
      "condition": "API·이벤트·파일 경계가 있으면",
      "required": ["계약 ID", "연결 요구", "입력", "출력", "오류", "접근 조건", "부작용", "재시도·중복 처리"],
      "labels": ["연결 요구", "입력", "출력", "오류", "접근 조건", "부작용", "재시도"],
      "checks": [
        { "unit": "table", "heading": "계약 일람", "columns": ["ID", "연결 요구"], "idPrefix": "API" },
        { "unit": "document", "labels": ["접근 조건", "부작용", "재시도"] }
      ]
    },
    {
      "type": "tech-data",
      "name": "데이터 설계",
      "spec": true,
      "condition": "영속 데이터가 있으면",
      "required": ["엔티티", "필드 의미", "키·관계·제약", "수명", "변경·마이그레이션"],
      "labels": [],
      "checks": []
    },
    {
      "type": "tech-ops",
      "name": "실행·운영",
      "spec": true,
      "condition": "배포·호스팅 또는 지속 실행 작업이 있으면",
      "required": ["실행 환경", "설정·비밀값 주입", "배포", "상태 확인", "장애·복구", "백업 대상 또는 불필요 사유"],
      "labels": [],
      "checks": []
    },
    {
      "type": "tasks",
      "name": "작업 정의",
      "spec": true,
      "condition": "구현 대상으로 정한 기능",
      "required": ["작업 ID", "목적", "연결 요구·설계", "포함·제외 범위", "선행 작업", "산출물", "검증 방법", "완료 조건"],
      "labels": ["목적", "근거", "범위", "선행", "산출물", "검증", "완료"],
      "checks": [
        { "unit": "section", "idPrefix": "TASK", "labels": ["근거", "선행", "산출물", "검증", "완료"] }
      ]
    },
    {
      "type": "proposal",
      "name": "검토 초안",
      "spec": false,
      "condition": "확정 전 대안을 정리할 때",
      "required": ["무엇을 제안하는지와 확정되지 않은 항목"],
      "labels": [],
      "checks": []
    },
    {
      "type": "record",
      "name": "경과 기록",
      "spec": false,
      "condition": "결정 경위를 남길 때",
      "required": ["결정한 내용과 근거", "어느 문서가 정본인지"],
      "labels": [],
      "checks": []
    },
    {
      "type": "guide",
      "name": "안내",
      "spec": false,
      "condition": "읽는 방법·착수 방법을 알릴 때",
      "required": ["대상과 먼저 읽을 문서"],
      "labels": [],
      "checks": []
    }
  ]
}
```

## 2. 검사기 변경

### 입력과 미검사

검사기의 입력에 `rules/spec-format.json`을 추가한다. 지금의 "적용 Spec 표가 없으면 미검사"에 "정의 파일이 없으면 미검사"를 더한다. 둘 중 하나라도 없으면 무엇이 있어야 하는지 알 수 없으므로 통과로 표시하지 않는다.

정의 파일이 있으나 JSON이 깨졌거나, 위 구조에 없는 필드·`unit`이 있거나, `type`이 중복이면 **오류**다. 미검사가 아니라 오류인 이유는 파일을 두고도 잘못 쓴 것이므로 고칠 위치가 분명하기 때문이다.

### C0 정의 파일과 규칙 표의 일치

사람이 읽는 spec-writing §3·§5 표를 남기는 대신 표와 정의 파일이 어긋나지 않게 기계가 본다. 검사 C0을 추가한다.

| 비교 | spec-writing의 표 | 정의 파일 |
|---|---|---|
| type 집합 | §3 표의 `type` 열 | `types[].type` |
| 필수 내용 | §3 표의 `필수 내용` 열을 `, `로 나눈 이름 | `types[].required` |
| 허용 라벨 | §5 라벨 이름 공간 표의 `허용하는 굵은 라벨` 열 | `types[].labels` |
| 검사 이름 | §5 검사 라벨 표에서 type마다 백틱으로 감싼 이름의 합집합 | `types[].checks[]`의 `labels`와 `columns`의 합집합 |

이름 집합이 같은지만 본다. 순서·검사 단위 열의 문장·표 밖 산문은 보지 않는다. 검사 단위 열은 사람용으로 남기고 기계는 정의 파일의 `unit`을 따른다.

C0은 `rules/spec-writing.md`가 있을 때만 적용한다. 정의 파일만 있는 저장소는 정의 파일이 정본이므로 C0 없이 C1·C2를 적용한다.

### 코드와 fixture

- `validate.py`의 `ALLOWED_TYPES`·`REQUIRED_LABELS`·`CONTRACT_TABLE_HEADING`·`CONTRACT_COLUMNS`·`CONTRACT_ID`를 지우고 정의 파일에서 읽은 값으로 대체한다. 판정 로직은 바꾸지 않는다.
- 기존 fixture 네 트리에 정의 파일을 넣는다. `no-settings/`는 적용 Spec 표만 없는 상태를 유지한다.
- 새 fixture를 더한다. 정의 파일 없음은 미검사, 깨진 JSON·모르는 `unit`·중복 `type`은 오류, §3·§5 표와 불일치는 C0 오류다.
- 오류가 재현되는 테스트를 먼저 쓰고 통과시킨다. [검증 규칙](../../rules/validation.md) §7 그대로다.

## 3. 규칙 문서 변경

| 문서 | 변경 |
|---|---|
| [검증 규칙](../../rules/validation.md) | §1에 C0 추가. §3 입력에 정의 파일 추가. §6 미검사 조건에 "정의 파일이 없다" 추가. §7에 "검사 항목을 늘릴 때 정의 파일의 `unit`을 먼저 정한다" 추가 |
| [문서 작성 규칙](../../rules/spec-writing.md) | §3·§5 표 앞에 "기계가 읽는 정본은 `rules/spec-format.json`이며 검사기가 이 표와의 일치를 확인한다"를 적는다. §5 끝의 "이 표를 고치면 검증기를 함께 고친다"를 "정의 파일을 함께 고친다"로 바꾼다 |
| [프로젝트 설정](../../rules/project-settings.md) | 적용 규칙 버전 표에 `포맷 정의` 행 추가 |
| [공통 Spec 구성안](../01-prd/spec-standard-proposal.md) | §7의 "후속 스키마 한 곳" 미결을 이 문서 링크로 닫는다 |
| [검사기 README](../../tools/spec-validator/README.md) | 제약 절의 상수 이름을 정의 파일로 바꾼다 |
| [구현계획](../04-tasks/implementation-plan.md) | TASK-004 산출물에 `rules/spec-format.json`을 추가한다. 확정 문서이므로 이 설계 승인과 함께 고친다 |

## 4. SyncDoc 쪽 설계

TASK-001 이후에 구현한다. 여기서는 정의 파일과 맞물리는 경계만 정한다.

### 수집

수집 worker가 snapshot을 만들 때 그 revision의 `rules/spec-format.json`과 `rules/project-settings.md`의 적용 Spec 표를 읽고 `docs/` 문서에 C1·C2를 적용한다. 결과는 snapshot에 저장한다. snapshot은 불변이므로 결과도 그 revision에 고정된다. 다른 revision의 결과를 섞지 않는다.

SyncDoc은 C0을 적용하지 않는다. C0은 저장소 안에서 규칙 표를 고치는 사람을 위한 검사이고, SyncDoc이 읽는 정본은 정의 파일 하나다.

Java 파서 `SpecMetadataParser`는 [`tools/spec-validator/fixtures/`](../../tools/spec-validator/fixtures/)를 그대로 테스트 입력으로 쓴다. 같은 fixture에서 Python 검사기와 같은 파일·항목을 오류로 내야 통과다. 두 구현의 판정이 갈리면 [검증 규칙](../../rules/validation.md)을 기준으로 고친다.

### 데이터

[데이터 설계](data-model.md)의 `document_snapshots`에 검사 결과 컬럼 하나를 더한다. 상태와 종류별 결과, 문서별 오류 목록을 담는다. 오류 항목은 경로·줄·검사 ID·내용이며, 문서가 `spec_id`를 가지면 함께 둔다. 별도 테이블은 두지 않는다. 조회 단위가 snapshot 하나이고 결과를 항목별로 질의할 요구가 없다.

### 계약

[API 계약](api-spec.md)의 계약 일람에 `API-024 GET /projects/{id}/spec-checklist`를 추가한다. 연결 요구는 REQ-008이다. 입력은 `snapshotId` 선택이고, 응답은 아래 형태다.

| 필드 | 의미 |
|---|---|
| `snapshotId`, `sourceRevision` | 결과가 속한 snapshot |
| `status` | `pass`, `error`, `pending`, `unchecked`. [검증 규칙](../../rules/validation.md) §6의 통과·오류·미작성·미검사와 같다 |
| `uncheckedReason` | `unchecked`일 때만. 정의 파일 없음, 적용 Spec 표 없음 중 하나 |
| `types[]` | 종류마다 `type`, `name`, `apply`(적용·보류·미적용), `status`, 해당 문서 목록, 오류 목록 |
| `types[].findings[]` | `documentId`, `path`, `line`, `check`(`C1`·`C2`), `message` |

접근 조건·부작용·재시도는 공통 절을 따르고 예외가 없다. 권한이 없으면 다른 계약처럼 404다. 첫 동기화 전에는 API-017처럼 빈 결과가 아니라 409다. 결과가 없는 것과 미검사를 구분하기 위해서다.

### 화면

[화면 명세](../02-ui-spec/ui-screens.md)의 UI-002 현황에 `### 산출물 체크리스트 규칙` 구획을 추가한다. 별도 화면은 만들지 않는다.

- 종류마다 한 행이다. 종류 이름, 적용 여부, 상태, 문서 수, 오류 수를 보인다.
- 행을 펼치면 오류 목록이 `경로:줄` 과 내용으로 나온다. 경로는 같은 snapshot의 문서 본문(UI-003)으로 이어진다.
- `unchecked`는 미검사로 표시하고 오류 0건이나 통과로 표시하지 않는다. `pending`은 미작성으로 표시하고 오류와 구분한다.
- 보류·미적용 종류는 사유를 함께 보인다.
- 기준을 고치는 조작은 없다. 정의 파일과 규칙 표는 저장소에서 고친다.

## 5. 확정되지 않은 항목

- **정의 파일의 스키마 검증 수준.** 이 문서는 필드와 `unit`을 정했지만 JSON Schema 파일을 따로 둘지는 정하지 않았다. 검사기가 구조를 직접 확인하는 것으로 시작하고, 다른 프로젝트가 정의 파일을 쓰기 시작하면 다시 본다.
- **API-024 응답의 크기 제한.** 오류가 수백 건인 저장소에서 목록을 자르는 기준을 정하지 않았다. 계약 추가 시 다른 목록 계약의 cursor 규칙을 따를지 정한다.
- **두 번째 샘플 프로젝트.** TASK-004 완료 조건인 두 프로젝트 적용은 아직 하지 않았다. 정의 파일을 그 프로젝트에도 두고 같은 검사기로 확인하는 것이 완료의 증거다.

## 6. 다음 순서

1. 이 설계 승인 후 `rules/spec-format.json`을 위 내용으로 만들고 프로젝트 설정 표에 행을 추가한다.
2. 검사기 fixture와 테스트를 먼저 고치고 `validate.py`를 정의 파일 로딩과 C0으로 바꾼다.
3. §3의 규칙 문서 여섯 건을 고치고 검사기를 실행해 기존 문서가 위반 상태가 되지 않는지 확인한다.
4. TASK-001 이후 데이터 설계·API-024·UI-002 구획을 확정 문서에 추가하고 `SpecMetadataParser`를 공유 fixture로 검증한다.
