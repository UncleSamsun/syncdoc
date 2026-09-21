---
id: DOC-L1
type: guide
---

# 검증용 문서 묶음

TASK-008 실제 흐름 검증에만 쓰는 임시 문서다. 이 브랜치(`lab/verification-fixtures`)는 `dev`·`main`으로 병합하지 않는다. 확인이 끝나면 지운다.

여기 있는 파일은 모두 이 저장소에서 만든 것이다. 다른 사람의 자료를 쓰지 않았다.

## 확인 대상

| 파일 | 무엇을 보는가 |
|---|---|
| `images/grid.png` | 수집한 그림이 서비스 주소로 바뀌어 화면에 그려지는가 |
| `images/wide.png` | 본문보다 넓은 그림이 화면을 밀어내지 않는가 |
| `images/not-really.png` | 이름만 `.png`이고 내용은 GIF인 파일을 거르는가 |
| `script-and-handlers.md` 외 7개 | 적대적 원문이 화면까지 가지 않는가 |

## 수집한 그림

![격자 무늬 그림](images/grid.png)

## 본문보다 넓은 그림

![가로로 긴 그림](images/wide.png)

## 이름과 내용이 어긋나는 파일

![png라고 이름 붙인 gif](images/not-really.png)

## 없는 그림

![수집하지 않은 그림](images/사라진그림.png)

## 받지 않는 형식

![svg는 스크립트를 담을 수 있다](images/logo.svg)

## 표와 다이어그램

| 항목 | 값 | 비고 |
|---|---|---|
| 상태 | 검증용 | 병합 대상 아님 |
| 만든 날 | 2026-09-21 | TASK-008 |

```mermaid
flowchart LR
  수집 --> 변환 --> 게시 --> 화면
```
