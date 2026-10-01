---
id: DOC-023
type: tasks
status: 확정
---

# GitHub Actions와 회사 서버 테스트 배포 작업계획

[회사 서버 테스트 CI/CD](../03-tech-spec/cicd.md)를 구현한다. 기존 기능과 GitHub 협업 규칙을 유지하며 작업은 현재 세션에서 순서대로 수행한다.

## TASK-014 검증된 revision을 회사 K3s에 배포

**목적:** main의 테스트 성공 revision을 반복 가능한 방식으로 회사 서버 테스트 환경에 배포한다.

**근거:** [실행과 운영](../03-tech-spec/ops.md)의 외부 배포 미검증 항목과 [CI/CD](../03-tech-spec/cicd.md). 사용자는 Jenkins를 제외하고 GitHub Actions를 선택했다. Issue #62.

**범위:** 기존 verify를 재사용 가능한 workflow로 만들고 main 검사 뒤 GHCR 게시와 배포 전용 브랜치 promotion을 추가한다. Kubernetes manifest·digest renderer·Flux bootstrap·health 확인·운영 문서를 만든다. 공개 HTTPS, 운영 환경, Jenkins, DB 자동 롤백과 main 자동 병합은 제외한다.

**선행:** TASK-008의 로컬 실제 흐름 검증, 회사 서버 K3s와 개인 Kubernetes 인증. 최초 Secret 주입과 GHCR 읽기 권한은 실제 앱 배포의 선행 조건이다.

**산출물:** `.github/workflows/verify.yml`, `.github/workflows/publish.yml`, `deploy/kubernetes/`, `deploy/flux/`, `tools/deployment/`, 이 문서와 CI/CD 운영 명세.

**검증:** renderer에서 잘못된 digest/저장소·누락된 이미지·동일 digest 재생성을 확인하고, actionlint로 workflow의 입력·출력·권한을 검사한다. Kustomize와 Kubernetes server dry-run으로 schema·권한을 확인한다. 서버 Flux controller Ready와 namespace 제한을 확인한다. 실제 게시와 로그인은 main 병합·자격증명 준비 이후 실행한다.

**완료:** 검증 성공 후에만 두 이미지 digest가 같은 배포 commit으로 반영되고, Flux의 배포 결과와 기능 시험 결과를 실제 근거로 보고한다. 준비되지 않은 Secret/이미지·미실행 시험은 완료로 표시하지 않는다.

### 실행 순서

- [x] renderer의 잘못된 입력과 manifest 오류를 재현하는 회귀 시험을 작성하고 실패를 확인한다.
- [x] digest renderer와 별도 테스트 DB/PVC·ClusterIP·probe·자원 제한 manifest를 구현하고 시험한다.
- [x] verify 재사용·main 게시·직렬 promotion workflow를 구현하고 정적 검사한다.
- [x] Flux controller·namespace/RBAC·suspended source를 준비하고 server dry-run·권한을 확인한다.
- [ ] 전체 diff·문서 검사·배포 검증을 수행하고 dev 대상 PR을 준비한다.
- [ ] main 반영 뒤 이미지 게시·Secret 주입·Flux resume·서버 health·사람 로그인/수집을 확인한다.

### 검토할 실패 조건

이미지 하나만 빌드된 경우 배포하지 않는다. 오래된 실행의 promotion을 막는다. private GHCR 읽기 실패를 readiness 성공으로 기록하지 않는다. suspend 해제 전에 Secret/게시본이 있어야 한다. 이전 이미지 복귀와 DB 복구는 다른 절차다.
