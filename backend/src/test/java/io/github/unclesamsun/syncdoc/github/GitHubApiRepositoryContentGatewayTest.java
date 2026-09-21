package io.github.unclesamsun.syncdoc.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 요청 제한에 걸린 응답을 어떻게 읽는지 확인한다.
 *
 * <p>여기 쓰인 상태 코드·헤더·본문은 2026-09-21에 실제 GitHub API에서 받아 본 것을 그대로 옮긴
 * 값이다. 미인증 한도(시간당 60)를 채웠을 때 GitHub는 429가 아니라 `403 rate limit exceeded`에
 * `x-ratelimit-remaining: 0`과 `x-ratelimit-reset`(epoch 초)을 실어 보내고 `retry-after`는 주지
 * 않는다. 429만 보고 있었다면 이 응답을 평범한 실패로 처리했을 것이다.
 */
class GitHubApiRepositoryContentGatewayTest {

    private static final String API = "https://api.github.test";
    /** 실제 응답의 본문이다. 사용자 IP만 지웠다. */
    private static final String RATE_LIMIT_BODY = """
            {"message":"API rate limit exceeded for 0.0.0.0. (But here's the good news: \
            Authenticated requests get a higher rate limit. Check out the documentation \
            for more details.)","documentation_url":"https://docs.github.com/rest/overview\
            /resources-in-the-rest-api#rate-limiting"}""";

    private MockRestServiceServer server;
    private GitHubApiRepositoryContentGateway gateway;

    @BeforeEach
    void setUp() {
        GitHubProperties properties = new GitHubProperties("Iv23test", "secret-value", "4895456", null,
                "http://localhost:5173/api/v1/auth/github/callback", "https://github.test", API);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new GitHubApiRepositoryContentGateway(properties,
                installationId -> "ghs_installation_token", builder);
    }

    @Test
    void a_real_rate_limited_403_is_read_as_a_rate_limit_and_keeps_the_reset_time() {
        server.expect(requestTo(API + "/repositories/1362321768"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("x-ratelimit-limit", "60")
                        .header("x-ratelimit-remaining", "0")
                        .header("x-ratelimit-used", "60")
                        .header("x-ratelimit-resource", "core")
                        .header("x-ratelimit-reset", "1789982817")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(RATE_LIMIT_BODY));

        assertThatThrownBy(() -> gateway.open("11", "1362321768"))
                .isInstanceOf(GitHubRateLimitedException.class)
                .satisfies(thrown -> assertThat(((GitHubRateLimitedException) thrown).retryAfter())
                        .isEqualTo(Instant.ofEpochSecond(1789982817)));
        server.verify();
    }

    @Test
    void a_forbidden_that_is_not_a_rate_limit_stays_an_ordinary_failure() {
        // 권한이 없어서 받은 403이다. 남은 한도가 있으므로 기다린다고 풀리지 않는다.
        server.expect(requestTo(API + "/repositories/1362321768"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("x-ratelimit-remaining", "4998")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Resource not accessible by integration\"}"));

        assertThatThrownBy(() -> gateway.open("11", "1362321768"))
                .isInstanceOf(GitHubLookupFailedException.class)
                .hasMessageContaining("403");
        server.verify();
    }

    @Test
    void a_429_with_retry_after_waits_the_number_of_seconds_it_was_given() {
        server.expect(requestTo(API + "/repositories/1362321768"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("retry-after", "60")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"You have exceeded a secondary rate limit.\"}"));

        Instant before = Instant.now();
        assertThatThrownBy(() -> gateway.open("11", "1362321768"))
                .isInstanceOf(GitHubRateLimitedException.class)
                .satisfies(thrown -> assertThat(((GitHubRateLimitedException) thrown).retryAfter())
                        .isBetween(before.plusSeconds(55), Instant.now().plusSeconds(65)));
        server.verify();
    }
}
