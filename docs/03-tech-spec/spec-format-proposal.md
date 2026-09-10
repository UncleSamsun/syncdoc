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
| YAML | 기각 | [검증 규칙](../../rules/validation.md) §8의 표준 라이브러리 제약 때문에 Python이 읽을 수 없다 |

## 1. 정의 파일

### 위치

대상 저장소 루트 기준 `rules/spec-format.json` 고정 경로다. [프로젝트 설정](../../rules/project-settings.md)이 `rules/project-settings.md`에 고정된 것과 같은 방식이다. 프로젝트 연결의 `docsRoot` 설정과 무관하게 `rules/`는 저장소 루트에서 찾는다. 파일이 없는 저장소는 미검사다.

### 구조

필드와 `checks[].unit` 세 종류의 정의는 2026-09-10 [검증 규칙](../../rules/validation.md) §3으로 승격했다. 여기에 중복 본문을 두지 않는다.

### 현재 포맷을 옮긴 내용

spec-writing §3·§5를 그대로 옮긴 첫 정의 파일이 [`rules/spec-format.json`](../../rules/spec-format.json)이다. 새 검사는 넣지 않았다.

## 2. 검사기 변경

### 입력과 미검사

검사기의 입력에 `rules/spec-format.json`을 추가한다. 지금의 "적용 Spec 표가 없으면 미검사"에 "정의 파일이 없으면 미검사"를 더한다. 둘 중 하나라도 없으면 무엇이 있어야 하는지 알 수 없으므로 통과로 표시하지 않는다.

정의 파일이 있으나 JSON이 깨졌거나, 위 구조에 없는 필드·`unit`이 있거나, `type`이 중복이면 **오류**다. 미검사가 아니라 오류인 이유는 파일을 두고도 잘못 쓴 것이므로 고칠 위치가 분명하기 때문이다.

### C0 정의 파일과 규칙 표의 일치

사람이 읽는 spec-writing §3·§5 표를 남기는 대신 표와 정의 파일이 어긋나지 않게 기계가 본다. 비교 대상과 판정은 [검증 규칙](../../rules/validation.md) §3으로 승격했다.

### 코드와 fixture

- `validate.py`의 `ALLOWED_TYPES`·`REQUIRED_LABELS`·`CONTRACT_TABLE_HEADING`·`CONTRACT_COLUMNS`·`CONTRACT_ID`를 지우고 정의 파일에서 읽은 값으로 대체한다. 판정 로직은 바꾸지 않는다.
- 기존 fixture 네 트리에 정의 파일을 넣는다. `no-settings/`는 적용 Spec 표만 없는 상태를 유지한다.
- 새 fixture를 더한다. 정의 파일 없음은 미검사, 깨진 JSON·모르는 `unit`·중복 `type`은 오류, §3·§5 표와 불일치는 C0 오류다.
- 오류가 재현되는 테스트를 먼저 쓰고 통과시킨다. [검증 규칙](../../rules/validation.md) §8 그대로다.

## 3. 규칙 문서 변경

| 문서 | 변경 |
|---|---|
| [검증 규칙](../../rules/validation.md) | §1에 C0 추가. §3에 정의 파일 구조와 판정 신설, 이후 절 번호 재정렬. 미검사 조건에 정의 파일 없음 추가 |
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
| `status` | `pass`, `error`, `pending`, `unchecked`. [검증 규칙](../../rules/validation.md) §7의 통과·오류·미작성·미검사와 같다 |
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
