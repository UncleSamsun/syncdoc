package io.github.unclesamsun.syncdoc.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 경계 설정.
 *
 * @param adminGithubUserId 최초 관리자의 GitHub 사용자 ID. serviceAdmin 판정의 유일한 근거다.
 * @param sessionTtl        세션 수명. 명세가 정하지 않은 제안값이다.
 * @param stateTtl          OAuth state 쿠키 수명. 명세가 정하지 않은 제안값이다.
 * @param cookieSecure      세션 쿠키에 Secure를 붙일지. 평문 http 로컬 개발에서만 false로 둔다.
 * @param csrfKey           CSRF 토큰 도출용 HMAC 키.
 * @param uninvitedPath     허용 목록에 없는 계정을 보낼 서비스 내부 경로.
 */
@ConfigurationProperties(prefix = "syncdoc.auth")
public record AuthProperties(
        String adminGithubUserId,
        Duration sessionTtl,
        Duration stateTtl,
        boolean cookieSecure,
        String csrfKey,
        String uninvitedPath) {

    public AuthProperties {
        sessionTtl = sessionTtl == null ? Duration.ofHours(12) : sessionTtl;
        stateTtl = stateTtl == null ? Duration.ofMinutes(10) : stateTtl;
        uninvitedPath = uninvitedPath == null || uninvitedPath.isBlank() ? "/uninvited" : uninvitedPath;
    }
}
