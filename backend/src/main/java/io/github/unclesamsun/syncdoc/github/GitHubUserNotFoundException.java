package io.github.unclesamsun.syncdoc.github;

/** 그 계정명을 가진 GitHub 사용자가 없다. 초대(API-006)의 입력 오류로 이어진다. */
public class GitHubUserNotFoundException extends RuntimeException {

    public GitHubUserNotFoundException(String login) {
        super("GitHub에 '" + login + "' 계정이 없다.");
    }
}
