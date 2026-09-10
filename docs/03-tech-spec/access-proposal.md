---
id: DOC-012
type: note
status: 검토
---

# GitHub 로그인과 접근 범위

## 방향

사용자는 GitHub OAuth 로그인, GitHub 열람 권한 준용, 초대된 사용자만 서비스 이용이라는 방향을 승인했다. 별도의 프로젝트 권한을 수동으로 중복 관리하지 않는다. 전통적인 OAuth App 또는 GitHub App의 사용자 인증 흐름 중 구체 앱 유형은 기술 설계에서 비교한다.

## 접근 판정 제안

열람 허용은 다음 조건을 모두 만족할 때만 한다.

1. GitHub 로그인으로 사용자 신원이 확인된다.
2. SyncDoc 이용 허용 목록에 포함되어 있다.
3. 해당 저장소/Project가 SyncDoc에서 관리 대상으로 연결되어 있다.
4. 요청 사용자에게 GitHub의 해당 리소스 열람 권한이 있고 앱에 부여된 권한·조직 정책 범위에서도 접근할 수 있다.

GitHub에서 볼 수 있는 모든 공개 저장소를 자동으로 수집한다는 뜻은 아니다. 연결 대상 목록과 사용자별 열람 권한은 구분한다. 계정명 변경에 영향받지 않도록 내부 사용자 식별에는 GitHub 사용자 ID를 사용하는 방안을 제안한다.

## 저장소와 Projects

저장소 문서·Issue·PR은 해당 리소스의 GitHub 접근 권한을 기준으로 한다. GitHub Project 자체의 공개 범위·읽기 권한은 저장소와 별개다. Project 안의 작업도 해당 저장소의 권한을 추가로 만족해야 한다.

여러 저장소의 집계는 사용자가 볼 수 있는 항목만 포함하고 집계 범위를 표시한다. 숨겨진 항목의 제목·담당자·개수·전체 완료율을 통하여 권한 밖 정보를 노출하지 않는다. Project를 읽을 수 없으면 저장소 자체의 허용된 정보와 Project 정보 미제공 상태를 구분한다.

## 로그인과 데이터 수집

로그인 성공 자체가 모든 저장소의 접근 승인이라는 뜻은 아니다. 서비스 전체 수집용 자격증명으로 가져온 캐시도 요청 사용자에게 반환하기 전에 리소스별 열람을 확인해야 한다. 검색·문서 HTML·내보내기·집계도 같은 판정을 적용한다.

GitHub 권한이 제거되거나 토큰이 무효화되면 캐시가 남아 있어도 열람을 계속 허용하지 않는다. 권한 재확인의 주기·캐시 수명과 GitHub 오류 시 차단 동작은 상세 설계에서 확정한다. 권한을 확인할 수 없는 경우 이전 허용 값을 무기한 신뢰하지 않는다.

## 구현 선택 사항

- 사용자 경험은 GitHub로 로그인하는 OAuth 흐름으로 한다는 제안이다. 전통적인 OAuth App과 GitHub App의 사용자 인증 흐름은 구현 선택지로 구분한다.
- OAuth App의 비공개 저장소 접근에 쓰는 repo scope는 쓰기 권한도 포함하며 비공개 저장소 전체에 대한 전용 읽기 전용 scope가 아니다. UI를 읽기 전용으로 만들었다고 토큰 권한도 읽기 전용이라고 설명하지 않는다.
- GitHub App은 저장소 선택과 세분화된 권한을 지원한다. 서버의 지속 수집과 읽기 위주 권한 설정에 적합한지 평가한다. 사용자 토큰은 앱과 사용자 양쪽 권한의 범위로 제한되지만 설치 토큰을 모든 사용자의 열람 권한으로 취급하지 않는다.
- 조직의 앱 접근 제한과 Projects 접근 권한을 함께 확인한다. OAuth App/GitHub App 등록, 권한 부여, 실제 토큰 발급은 아직 수행하지 않았다.

## 다음 확인

GitHub 로그인·권한 준용이라는 제품 방향을 바탕으로 OAuth App 또는 GitHub App의 사용자 인증/서버 수집 방식을 선택하고, 초대 방법과 권한 변경 반영 기준을 구체화한다. 별도 프로젝트 역할 체계를 먼저 구현하지 않는다.

## 공식 근거

- [OAuth App scope](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps)
- [OAuth Apps와 GitHub Apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps)
- [Project 공개 범위](https://docs.github.com/en/issues/planning-and-tracking-with-projects/managing-your-project/managing-visibility-of-your-projects)
