# 문서 검증기

`docs/` 아래 명세가 [문서 작성 규칙](../../rules/spec-writing.md)을 지키는지 검사한다. 검사 범위와 판정 기준은 [검증 규칙](../../rules/validation.md)이 정본이다.

## 실행

```bash
python tools/spec-validator/validate.py
```

저장소 루트에서 인자 없이 실행하면 `docs/`와 `rules/project-settings.md`를 읽는다. 오류가 있으면 종료 코드 1, 없으면 0이다.

```bash
python tools/spec-validator/validate.py <DOCS_DIR> <SETTINGS_FILE>
```

## 테스트

```bash
cd tools/spec-validator && python -m unittest test_validate -v
```

`fixtures/` 아래 네 트리로 검사한다.

| fixture | 확인하는 것 |
|---|---|
| `ok/` | 통과, 보류 종류의 미작성 보고, 미적용 종류 무시, `guide`의 라벨 검사 생략, 계약 일람 표의 채워진 행과 여분 열 |
| `bad-c1/` | 적용 문서 없음, 보류 사유 없음, frontmatter 없음, 표에 없는 type |
| `bad-c2/` | 필수 라벨 없음, 빈 라벨, 검사 단위 0개, 요구의 `예외`·`근거` 누락, 작업의 `선행`·`산출물`·`검증` 누락, 계약 일람 표의 빈 칸·잘못된 `API-NNN`·표 자체 없음 |
| `no-settings/` | 적용 Spec 표가 없을 때 미검사 보고 |

## 제약

- Python 표준 라이브러리만 쓴다. 외부 의존성을 추가하지 않는다.
- 문서를 고치지 않는다. 읽고 보고만 한다.
- 검사 항목을 늘릴 때 [검증 규칙](../../rules/validation.md)을 먼저 고친다. 문서에 없는 검사를 코드에만 넣지 않는다.
- `validate.py`의 `ALLOWED_TYPES`·`REQUIRED_LABELS`·`CONTRACT_COLUMNS`는 [문서 작성 규칙](../../rules/spec-writing.md) §3·§5의 표를 따른다. 표를 고치면 함께 고친다.
