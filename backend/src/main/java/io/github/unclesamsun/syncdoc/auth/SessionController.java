package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.common.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-003 내 정보, API-004 로그아웃. */
@RestController
@RequestMapping(ApiPaths.BASE)
public class SessionController {

    private final SessionService sessions;
    private final SessionCookies cookies;

    public SessionController(SessionService sessions, SessionCookies cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    public record MeResponse(UUID id, String githubUserId, String login, boolean serviceAdmin, String csrfToken) {
    }

    /** API-003. */
    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        CurrentUser user = (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE);
        String rawToken = (String) request.getAttribute(SessionAuthenticationFilter.RAW_TOKEN_ATTRIBUTE);
        return new MeResponse(user.id(), user.githubUserId(), user.login(), user.serviceAdmin(),
                sessions.csrfTokenFor(rawToken));
    }

    /** API-004. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.revoke((String) request.getAttribute(SessionAuthenticationFilter.RAW_TOKEN_ATTRIBUTE));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expired().toString())
                .build();
    }
}
