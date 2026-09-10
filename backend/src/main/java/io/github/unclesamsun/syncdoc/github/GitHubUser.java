package io.github.unclesamsun.syncdoc.github;

/** GitHub가 알려준 사용자. 내부 식별은 숫자 ID를 문자열로 보존한 githubUserId로 한다. login은 바뀔 수 있다. */
public record GitHubUser(String githubUserId, String login) {
}
