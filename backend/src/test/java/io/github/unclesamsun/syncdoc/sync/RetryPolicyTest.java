package io.github.unclesamsun.syncdoc.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 재시도 간격. 데이터 설계가 정한 최소 60초·최대 15분과 GitHub 시각 우선을 확인한다. */
class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(new SyncProperties(
            null, null, null, null, null, 0, 0, 0, null, null));
    private final Instant now = Instant.parse("2026-09-11T00:00:00Z");

    @Test
    void the_first_retry_waits_the_minimum() {
        assertThat(policy.backoff(1)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void repeated_failures_back_off_further_each_time() {
        assertThat(policy.backoff(2)).isEqualTo(Duration.ofSeconds(120));
        assertThat(policy.backoff(3)).isEqualTo(Duration.ofSeconds(240));
    }

    @Test
    void the_wait_never_grows_past_the_maximum() {
        assertThat(policy.backoff(10)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.backoff(1000)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void a_later_time_from_github_wins() {
        Instant githubSaid = now.plus(Duration.ofMinutes(30));

        assertThat(policy.nextAttemptAt(1, now, githubSaid)).isEqualTo(githubSaid);
    }

    @Test
    void an_earlier_time_from_github_does_not_shorten_our_wait() {
        assertThat(policy.nextAttemptAt(3, now, now.plusSeconds(5)))
                .isEqualTo(now.plus(Duration.ofSeconds(240)));
    }
}
