package io.github.unclesamsun.syncdoc.github;

/**
 * 앱과 사용자가 모두 접근할 수 있는 저장소 하나.
 *
 * @param githubRepositoryId 숫자 ID를 문자열로 보존한 값. 저장소 이름이 바뀌어도 이 값은 그대로다
 * @param fullName           `소유자/이름`. 표시와 REST 경로에 쓴다
 * @param isPrivate          비공개 여부
 * @param defaultBranch      저장소 기본 브랜치
 * @param installationId     이 저장소를 볼 수 있게 해 준 앱 설치의 GitHub ID
 */
public record GitHubRepository(
        String githubRepositoryId,
        String fullName,
        boolean isPrivate,
        String defaultBranch,
        String installationId) {
}
