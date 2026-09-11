package io.github.unclesamsun.syncdoc.sync;

import java.time.Duration;
import java.time.Instant;

/**
 * 다음 재시도 시각을 정한다. 시도가 거듭될수록 간격을 늘리되 상한을 둔다.
 *
 * <p>GitHub가 재시도 시각을 알려준 경우(429의 {@code Retry-After}, rate limit reset)에는
 * 그 시각을 우선한다. 우리 계산이 더 이르면 GitHub가 말한 시각까지 기다린다.
 */
public class RetryPolicy {

    private final SyncProperties properties;

    public RetryPolicy(SyncProperties properties) {
        this.properties = properties;
    }

    public Instant nextAttemptAt(int attempt, Instant now, Instant githubRetryAfter) {
        Instant ours = now.plus(backoff(attempt));
        if (githubRetryAfter == null) {
            return ours;
        }
        return githubRetryAfter.isAfter(ours) ? githubRetryAfter : ours;
    }

    /** 1회 실패는 최소 간격, 이후 2배씩 늘리고 상한에서 멈춘다. */
    Duration backoff(int attempt) {
        long min = properties.minRetryDelay().toSeconds();
        long max = properties.maxRetryDelay().toSeconds();
        int steps = Math.max(attempt, 1) - 1;
        // 지수 계산이 넘치지 않게 상한에 닿는 순간 멈춘다.
        long seconds = min;
        for (int i = 0; i < steps && seconds < max; i++) {
            seconds = seconds * 2;
        }
        return Duration.ofSeconds(Math.min(seconds, max));
    }
}
