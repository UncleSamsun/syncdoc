package io.github.unclesamsun.syncdoc.github;

/** GitHub 조회 실패. 메시지에 토큰을 담지 않는다. */
public class GitHubLookupFailedException extends RuntimeException {

    public GitHubLookupFailedException(String reason) {
        super("GitHub 조회에 실패했다: " + reason);
    }
}
