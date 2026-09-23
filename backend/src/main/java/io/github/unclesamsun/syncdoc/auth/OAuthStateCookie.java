package io.github.unclesamsun.syncdoc.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 로그인 시작에서 콜백까지 state·code_verifier·returnTo를 나른다.
 * 콜백이 이 쿠키를 지우므로 같은 state를 두 번 쓸 수 없다. 서버에 별도 표를 두지 않는다.
 */
@Component
public class OAuthStateCookie {

    public static final String NAME = "SYNCDOC_OAUTH";

    private final AuthProperties properties;

    public OAuthStateCookie(AuthProperties properties) {
        this.properties = properties;
    }

    public record Pending(String state, String codeVerifier, String returnTo) {
    }

    public ResponseCookie issued(Pending pending) {
        String packed = pending.state() + "\n" + pending.codeVerifier() + "\n" + pending.returnTo();
        String value = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(packed.getBytes(StandardCharsets.UTF_8));
        return base(value).maxAge(properties.stateTtl()).build();
    }

    public ResponseCookie expired() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<Pending> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                try {
                    String packed = new String(Base64.getUrlDecoder().decode(cookie.getValue()),
                            StandardCharsets.UTF_8);
                    String[] parts = packed.split("\n", 3);
                    if (parts.length == 3) {
                        return Optional.of(new Pending(parts[0], parts[1], parts[2]));
                    }
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/");
    }
}
