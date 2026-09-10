package io.github.unclesamsun.syncdoc.github;

/** GitHub 사용자 신원을 확인하는 포트. REQ-001의 GitHub App 사용자 인증이 구현 후보다. */
public interface GitHubIdentityGateway {

    /** 사용자 access token의 주인을 확인한다. */
    GitHubUser fetchAuthenticatedUser(String userAccessToken);

    /** 계정명으로 사용자를 찾는다. 초대(API-006)가 계정명을 안정된 ID로 바꿀 때 쓴다. */
    GitHubUser fetchUserByLogin(String login);
}
