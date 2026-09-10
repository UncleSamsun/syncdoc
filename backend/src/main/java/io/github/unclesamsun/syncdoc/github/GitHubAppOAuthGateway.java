package io.github.unclesamsun.syncdoc.github;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * GitHub App 사용자 인증의 실제 구현.
 *
 * <p>GitHub는 교환에 실패해도 HTTP 200에 `error` 본문을 담아 돌려주므로 상태 코드만 보고 성공으로 읽지 않는다.
 * 예외 메시지에는 code·client secret·토큰을 담지 않는다.
 */
public class GitHubAppOAuthGateway implements GitHubOAuthGateway {

    private final GitHubProperties properties;
    private final RestClient client;
    private final Clock clock;

    public GitHubAppOAuthGateway(GitHubProperties properties, RestClient.Builder builder, Clock clock) {
        this.properties = properties;
        this.client = builder.build();
        this.clock = clock;
    }

    @Override
    public String authorizeUrl(String state, String codeChallenge) {
        // 값마다 직접 인코딩한다. UriComponentsBuilder는 query 안의 `:`와 `/`를 그대로 두므로
        // redirect_uri가 등록값과 문자 단위로 다르게 보일 수 있다.
        return properties.oauthBaseUrl() + "/login/oauth/authorize"
                + "?client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=S256";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public GitHubTokens exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = baseForm();
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        form.add("code_verifier", codeVerifier);
        return postForTokens(form);
    }

    @Override
    public GitHubTokens refreshTokens(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postForTokens(form);
    }

    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }

    private GitHubTokens postForTokens(MultiValueMap<String, String> form) {
        Map<String, Object> body;
        try {
            body = client.post()
                    .uri(properties.oauthBaseUrl() + "/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .body(form)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException e) {
            throw new GitHubExchangeFailedException("GitHub에 연결하지 못했다");
        }
        if (body == null) {
            throw new GitHubExchangeFailedException("응답이 비어 있다");
        }
        Object error = body.get("error");
        if (error != null) {
            throw new GitHubExchangeFailedException(String.valueOf(error));
        }
        Object accessToken = body.get("access_token");
        if (accessToken == null) {
            throw new GitHubExchangeFailedException("응답에 access_token이 없다");
        }
        Instant now = clock.instant();
        return new GitHubTokens(
                String.valueOf(accessToken),
                body.get("refresh_token") == null ? null : String.valueOf(body.get("refresh_token")),
                expiryOf(body.get("expires_in"), now),
                expiryOf(body.get("refresh_token_expires_in"), now));
    }

    /** GitHub는 남은 초를 준다. 저장은 절대 시각으로 한다. */
    private static Instant expiryOf(Object secondsFromNow, Instant now) {
        if (secondsFromNow instanceof Number seconds) {
            return now.plusSeconds(seconds.longValue());
        }
        return null;
    }
}
