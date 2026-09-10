package io.github.unclesamsun.syncdoc.github;

/** 사용자 access token으로 GitHub 사용자 신원을 확인하는 포트. REQ-001의 GitHub App 사용자 인증이 구현 후보다. */
public interface GitHubIdentityGateway {

    GitHubUser fetchAuthenticatedUser(String userAccessToken);
}
