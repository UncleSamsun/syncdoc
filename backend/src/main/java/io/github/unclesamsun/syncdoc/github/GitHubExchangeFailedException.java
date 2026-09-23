package io.github.unclesamsun.syncdoc.github;

/** 토큰 교환·갱신 실패. 메시지에 code·client secret·토큰을 담지 않는다. */
public class GitHubExchangeFailedException extends RuntimeException {

    public GitHubExchangeFailedException(String reason) {
        super("GitHub 토큰 교환에 실패했다: " + reason);
    }
}
