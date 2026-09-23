package io.github.unclesamsun.syncdoc.github;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * App JWT로 설치 토큰을 발급받는다. 설치 토큰은 1시간짜리라 만료 직전까지 재사용한다.
 * 갱신 여유는 사용자 토큰과 같은 60초로 맞춘다.
 */
public class GitHubAppInstallationTokenGateway implements InstallationTokenGateway {

    /** 만료 직전에 쓰다가 요청 도중 만료되는 일을 막는 여유다. */
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private record CachedToken(String token, Instant expiresAt) {
    }

    private final GitHubProperties properties;
    private final AppPrivateKey privateKey;
    private final RestClient client;
    private final Clock clock;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public GitHubAppInstallationTokenGateway(GitHubProperties properties, RestClient.Builder builder,
                                             Clock clock) {
        this.properties = properties;
        this.privateKey = AppPrivateKey.parse(properties.privateKey());
        this.client = builder.build();
        this.clock = clock;
    }

    @Override
    public String accessToken(String githubInstallationId) {
        Instant now = clock.instant();
        CachedToken cached = cache.get(githubInstallationId);
        if (cached != null && cached.expiresAt().isAfter(now.plus(REFRESH_MARGIN))) {
            return cached.token();
        }
        CachedToken issued = issue(githubInstallationId, now);
        cache.put(githubInstallationId, issued);
        return issued.token();
    }

    private CachedToken issue(String githubInstallationId, Instant now) {
        try {
            Map<String, Object> body = client.post()
                    .uri(properties.apiBaseUrl() + "/app/installations/" + githubInstallationId
                            + "/access_tokens")
                    .headers(headers -> {
                        headers.set("Authorization", "Bearer " + privateKey.jwt(properties.appId(), now));
                        headers.set("Accept", "application/vnd.github+json");
                        headers.set("X-GitHub-Api-Version", "2022-11-28");
                    })
                    .retrieve()
                    .body(JSON_OBJECT);
            if (body == null || body.get("token") == null) {
                throw new GitHubLookupFailedException("설치 토큰 응답에 토큰이 없다");
            }
            return new CachedToken(String.valueOf(body.get("token")), expiresAt(body.get("expires_at"), now));
        } catch (HttpClientErrorException e) {
            // 설치가 지워졌거나 권한이 바뀐 경우다. 토큰 값은 어느 경로로도 남기지 않는다.
            throw new GitHubLookupFailedException("설치 토큰 발급이 " + e.getStatusCode().value() + "로 거절됐다");
        } catch (RestClientException e) {
            throw new GitHubLookupFailedException("설치 토큰을 발급받지 못했다");
        }
    }

    private static Instant expiresAt(Object value, Instant now) {
        try {
            return Instant.parse(String.valueOf(value));
        } catch (RuntimeException e) {
            // 응답에 만료가 없으면 보수적으로 짧게 잡는다. 실제 만료는 1시간이다.
            return now.plus(Duration.ofMinutes(30));
        }
    }
}
