package io.github.unclesamsun.syncdoc.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub App 설정.
 *
 * @param clientId     사용자 인증에 쓰는 공개 식별자
 * @param clientSecret 토큰 교환에 쓰는 비밀값. 응답·로그에 넣지 않는다
 * @param appId        설치 토큰 발급에 쓰는 앱 ID. 지금은 기록만 하고 쓰지 않는다
 * @param redirectUri  GitHub에 등록한 콜백 주소와 정확히 같아야 한다
 * @param oauthBaseUrl 로그인·토큰 교환 호스트. 테스트가 다른 값을 넣는다
 * @param apiBaseUrl   REST API 호스트. 테스트가 다른 값을 넣는다
 */
@ConfigurationProperties(prefix = "syncdoc.github")
public record GitHubProperties(
        String clientId,
        String clientSecret,
        String appId,
        String redirectUri,
        String oauthBaseUrl,
        String apiBaseUrl) {

    public GitHubProperties {
        redirectUri = blankToDefault(redirectUri, "http://localhost:5173/api/v1/auth/github/callback");
        oauthBaseUrl = stripTrailingSlash(blankToDefault(oauthBaseUrl, "https://github.com"));
        apiBaseUrl = stripTrailingSlash(blankToDefault(apiBaseUrl, "https://api.github.com"));
    }

    /** 사용자 로그인에 필요한 값이 모두 있는지. 없으면 미설정 게이트웨이를 쓴다. */
    public boolean isUserAuthConfigured() {
        return notBlank(clientId) && notBlank(clientSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToDefault(String value, String fallback) {
        return notBlank(value) ? value.trim() : fallback;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
