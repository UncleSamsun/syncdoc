---
id: DOC-022
type: tech-ops
status: 확정
---

# 회사 서버 테스트 CI/CD

GitHub Actions로 검증·이미지를 만들고 회사 K3s의 Flux가 배포 전용 Git 브랜치를 읽는다. Jenkins는 사용하지 않는다. [실행과 운영](ops.md)의 로컬 구성에 추가되는 회사 서버 테스트 환경이다.

## 실행 환경

GitHub-hosted Ubuntu runner에서 Java 25·Node 22·Python 3.12 검증과 Docker 이미지 빌드를 수행한다. 회사 K3s에는 `syncdoc-test` namespace와 PostgreSQL 17·backend·web을 둔다. Flux v2.9.5의 source/kustomize controller만 사용한다. CI 실행 코드는 회사 서버에서 실행하지 않는다.

웹은 ClusterIP로만 제공한다. 서버에서 `kubectl -n syncdoc-test port-forward --address 127.0.0.1 service/web 8081:80`을 실행하고 PC에서 `ssh -L 8081:127.0.0.1:8081 ai-server`로 접속한다. OAuth redirect는 `http://localhost:8081/api/v1/auth/github/callback`이다. 이 터널 테스트에서는 cookie secure를 끈다. 공용 HTTPS·Ingress는 범위 밖이다.

## 설정·비밀값 주입

서버의 `syncdoc-test` namespace에 `syncdoc-secrets` Secret을 별도로 만든다. DB 암호·token key·CSRF key는 테스트 환경 전용 값으로 생성한다. 사용자가 선택한 기존 GitHub App을 재사용한다. Secret과 실제 `.env`는 소스·배포 브랜치·이미지·CI artifact에 넣지 않는다. 주입 키는 `SYNCDOC_DB_PASSWORD`, `SYNCDOC_TOKEN_KEY`, `SYNCDOC_CSRF_KEY`, `SYNCDOC_ADMIN_GITHUB_USER_ID`, `SYNCDOC_GITHUB_APP_ID`, `SYNCDOC_GITHUB_CLIENT_ID`, `SYNCDOC_GITHUB_CLIENT_SECRET`, `SYNCDOC_GITHUB_PRIVATE_KEY`, 선택적인 `SYNCDOC_GITHUB_WEBHOOK_SECRET`뿐이다. 키 의미는 [`deploy/.env.example`](../../deploy/.env.example)을 참조하되 그 파일 전체를 Secret으로 가져오지 않는다. DB 주소·콜백 등 비밀값이 아닌 설정은 ConfigMap에서만 읽는다.

GHCR 이미지를 공개로 설정하거나, 읽기 전용 registry 자격증명으로 `ghcr-pull` imagePullSecret을 namespace에 등록해야 한다. Actions 게시에는 `GITHUB_TOKEN`의 packages write 권한을 쓴다. 서버는 GitHub 관리 토큰·SSH 비밀번호를 받지 않는다.

Flux Kustomization은 `syncdoc-deployer` ServiceAccount를 impersonate한다. namespace 범위의 Deployment·Service·ConfigMap·PVC만 관리하고 Namespace·RBAC·Secret은 초기 운영자가 준비한다. Flux controller 설치 자체에는 cluster 권한이 필요하다.

## 배포

1. PR에서는 기존 검증과 배포 renderer 회귀 시험만 수행한다. 이미지를 게시하거나 배포하지 않는다.
2. main push 또는 main에서 수동 실행하면 같은 revision의 검증을 먼저 수행한다.
3. backend/web 이미지를 GHCR에 `sha-<commit>` 태그로 게시하고 각각 digest를 받는다.
4. renderer가 두 digest를 `deploy/kubernetes` manifest에 넣고, `syncdoc-test-deploy` 브랜치의 `kubernetes/`에 한 commit으로 반영한다. main/dev를 수정하지 않는다. 게시 실패 시 배포 브랜치를 갱신하지 않는다.
5. Flux가 그 브랜치를 1분 주기로 가져와 적용한다. 리소스에는 source revision을 annotation으로 남긴다. 오래된 실행이 뒤늦게 배포하는 것을 막기 위해 main HEAD 일치 여부를 게시 직전에 확인하고 CI/CD 실행을 직렬화한다.

최초 controller·namespace·RBAC 설치와 Git source 등록은 [`deploy/flux/bootstrap.sh`](../../deploy/flux/bootstrap.sh)를 사용한다. Secret/이미지가 준비되기 전에는 Flux Kustomization을 suspend 상태로 두고 명시적으로 resume한다. 최초 배포 브랜치는 CI가 만든다. Flux source 오류만으로 기동 성공을 주장하지 않는다.

## 상태 확인

`kubectl -n syncdoc-cd get gitrepositories,kustomizations`와 `kubectl -n syncdoc-test get deployments,pods,pvc`로 source revision과 Ready 조건을 확인한다. Kustomization은 세 Deployment의 readiness를 기다린다. `bash deploy/flux/check.sh <Actions summary의 배포 commit>`은 source/applied revision이 그 commit과 같은지 기다린 뒤 Ready와 rollout을 확인하고 port-forward로 웹·live·ready 200 및 미인증 `/api/v1/me`의 401을 검사한다. GitHub 실제 로그인·수집·그림·권한·E2E는 [검증 기록](../04-tasks/mvp-verification-record.md)의 절차로 추가 수행한다. Actions의 성공은 이미지와 배포 요청 게시 성공이며 서버 배포 성공과 별개다.

### 최초 준비 명령

서버 작업 폴더에 `deploy/flux/` 파일을 준비하고 `bash deploy/flux/bootstrap.sh`를 실행한다. 비밀값은 별도 Secret으로 준비한다. 첫 main workflow가 성공하면 GHCR package 공개 여부 또는 읽기 전용 `ghcr-pull` Secret을 확인하고 다음을 실행한다.

```bash
kubectl -n syncdoc-cd patch kustomization syncdoc-test --type merge -p '{"spec":{"suspend":false}}'
bash deploy/flux/check.sh <배포-commit>
```

## 장애·복구

잘못된 이미지·기동 오류는 Flux/Deployment Ready=False 및 Pod 상태로 확인한다. 자동 DB 롤백은 하지 않는다. 배포 브랜치에서 직전 정상 manifest commit으로 되돌리는 새 commit을 만들고 같은 health 검사를 반복한다. DB migration과 이전 코드의 호환 여부를 먼저 확인한다. Secret을 변경해도 Deployment가 자동으로 재시작되는 것은 아니므로 필요하면 운영자가 rollout restart한다.

prune는 끈다. 배포 Git에서 파일이 빠졌다는 이유로 DB/PVC를 지우지 않는다. namespace 삭제·PVC 삭제·서버 재부팅은 자동 배포에 포함하지 않는다. 최초 준비 후 일상 배포마다 sudo 인증이 필요하지 않다.

## 백업 대상

테스트 PostgreSQL PVC와 DB 밖의 token key를 별도로 보관한다. 테스트에서도 첨부 포함 `pg_dump`/별도 DB 복원 비교를 수행한다. 배포 Git·이미지는 재생성 가능하나 이전 정상 digest의 보존 정책이 있어야 한다. 단일 서버의 저장소는 서버 외부 백업을 대신하지 않는다.

## 구현·서버 검증 경계

workflow 정적 검사·renderer 실패 경로·Kustomize/서버 dry-run 통과와 실제 이미지 게시·로그인·수집·서버 복원 시험을 구분한다. main 병합은 사람의 판단이며 이 작업에서 자동 병합하지 않는다.
