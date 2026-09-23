# 문서 검증기

`docs/` 아래 명세가 [문서 작성 규칙](../../rules/spec-writing.md)을 지키는지 검사한다. 검사 범위와 판정 기준은 [검증 규칙](../../rules/validation.md)이 정본이다.

## 실행

```bash
python tools/spec-validator/validate.py
```

저장소 루트에서 인자 없이 실행하면 현재 폴더를 루트로 보고 `rules/spec-format.json`, `rules/project-settings.md`, 있으면 `rules/spec-writing.md`, 그리고 `docs/`를 읽는다. 오류가 있으면 종료 코드 1, 없으면 0이다.

```bash
python tools/spec-validator/validate.py <ROOT> [DOCS_ROOT]
```

## 테스트

```bash
cd tools/spec-validator && python -m unittest test_validate -v
```

`fixtures/` 아래 각 트리는 실제 저장소와 같은 배치(`docs/`, `rules/`)다. SyncDoc의 Java 파서도 같은 fixture로 판정 일치를 확인한다.

| fixture | 확인하는 것 |
|---|---|
| `ok/` | 통과, 보류 종류의 미작성 보고, 미적용 종류 무시, `checks`가 빈 종류의 라벨 검사 생략, 계약 일람 표의 채워진 행과 여분 열, 규칙 표와 정의 파일의 일치 |
| `bad-c0/` | 규칙 표의 필수 내용 누락, 정의 파일에 없는 type, 허용 라벨 초과, 검사 이름 누락 |
| `bad-c1/` | 적용 문서 없음, 보류 사유 없음, frontmatter 없음, 정의 파일에 없는 type |
| `bad-c2/` | 필수 라벨 없음, 빈 라벨, 검사 단위 0개, 요구의 `예외`·`근거` 누락, 작업의 `선행`·`산출물`·`검증` 누락, 계약 일람 표의 빈 칸·잘못된 `API-NNN`·표 자체 없음 |
| `no-settings/` | 적용 Spec 표가 없을 때 미검사 보고 |
| `no-format/` | 정의 파일이 없을 때 미검사 보고 |
| `bad-format-syntax/`, `bad-format-unit/`, `bad-format-duplicate/` | 깨진 JSON, 모르는 `unit`, 중복 `type`이 C0 오류 |

## 제약

- Python 표준 라이브러리만 쓴다. 외부 의존성을 추가하지 않는다.
- 문서를 고치지 않는다. 읽고 보고만 한다.
- 검사 항목을 늘릴 때 [검증 규칙](../../rules/validation.md)을 먼저 고친다. 문서에 없는 검사를 코드에만 넣지 않는다.
- 문서 종류·라벨·검사는 코드 상수가 아니라 `rules/spec-format.json`에서 읽는다. 라벨을 바꿀 때 코드를 고치지 않는다. 검사 단위(`unit`)를 새로 만들 때만 [검증 규칙](../../rules/validation.md)과 코드를 함께 고친다.
