package io.github.unclesamsun.syncdoc.github;

import java.time.Instant;

/**
 * GitHub가 요청 제한을 걸었다. GitHub가 알려준 재시도 시각을 그대로 전달해
 * 우리 계산보다 이른 시각에 다시 부르지 않게 한다.
 */
public class GitHubRateLimitedException extends RuntimeException {

    private final Instant retryAfter;

    public GitHubRateLimitedException(Instant retryAfter) {
        super("GitHub가 요청 제한을 걸었다");
        this.retryAfter = retryAfter;
    }

    /** GitHub가 시각을 주지 않았으면 null이다. 그때는 우리 backoff만 쓴다. */
    public Instant retryAfter() {
        return retryAfter;
    }
}
