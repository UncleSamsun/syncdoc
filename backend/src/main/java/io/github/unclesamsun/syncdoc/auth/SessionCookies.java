package io.github.unclesamsun.syncdoc.auth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 세션 쿠키를 만드는 한 곳. HttpOnly와 SameSite를 여기서만 정한다. */
@Component
public class SessionCookies {

    private final AuthProperties properties;

    public SessionCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie issued(String rawToken, Instant expiresAt) {
        return base(rawToken).maxAge(Duration.between(Instant.now(), expiresAt)).build();
    }

    public ResponseCookie expired() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(SessionService.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/");
    }
}
