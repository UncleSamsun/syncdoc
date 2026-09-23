package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.common.ApiPaths;
import io.github.unclesamsun.syncdoc.github.GitHubIdentityGateway;
import io.github.unclesamsun.syncdoc.github.GitHubOAuthGateway;
import io.github.unclesamsun.syncdoc.github.GitHubTokens;
import io.github.unclesamsun.syncdoc.github.GitHubUser;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-001 로그인 시작, API-002 콜백. 세션은 허용 목록을 통과한 뒤에만 발급한다. */
@RestController
@RequestMapping(ApiPaths.BASE + "/auth/github")
public class OAuthController {

    private static final String LOGIN_WITH_STATE_ERROR = "/login?error=state";

    private final GitHubOAuthGateway oauth;
    private final GitHubIdentityGateway identity;
    private final LoginService logins;
    private final SessionCookies sessionCookies;
    private final OAuthStateCookie stateCookie;
    private final SecureRandom random = new SecureRandom();

    public OAuthController(GitHubOAuthGateway oauth, GitHubIdentityGateway identity, LoginService logins,
                           SessionCookies sessionCookies, OAuthStateCookie stateCookie) {
        this.oauth = oauth;
        this.identity = identity;
        this.logins = logins;
        this.sessionCookies = sessionCookies;
        this.stateCookie = stateCookie;
    }

    /** API-001. */
    @GetMapping("/start")
    public ResponseEntity<Void> start(@RequestParam(required = false) String returnTo) {
        String state = randomUrlSafe();
        String codeVerifier = randomUrlSafe();
        OAuthStateCookie.Pending pending =
                new OAuthStateCookie.Pending(state, codeVerifier, ReturnToPolicy.sanitize(returnTo));
        return ResponseEntity.status(302)
                .location(URI.create(oauth.authorizeUrl(state, challengeOf(codeVerifier))))
                .header(HttpHeaders.SET_COOKIE, stateCookie.issued(pending).toString())
                .build();
    }

    /** API-002. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         HttpServletRequest request) {
        Optional<OAuthStateCookie.Pending> pending = stateCookie.read(request);
        if (code == null || state == null || pending.isEmpty()
                || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
                pending.get().state().getBytes(StandardCharsets.UTF_8))) {
            return redirect(LOGIN_WITH_STATE_ERROR, null);
        }

        GitHubTokens tokens;
        GitHubUser user;
        try {
            tokens = oauth.exchangeCode(code, pending.get().codeVerifier());
            user = identity.fetchAuthenticatedUser(tokens.accessToken());
        } catch (RuntimeException e) {
            // 실패 원인을 사용자에게 흘리지 않는다. 세션도 발급하지 않는다.
            return redirect(LOGIN_WITH_STATE_ERROR, null);
        }

        Optional<SessionService.IssuedSession> issued = logins.completeLogin(user, tokens);
        if (issued.isEmpty()) {
            // 허용 목록에 없으면 세션을 주지 않는다. 그 화면은 프로젝트의 존재 여부를 알려주지 않는다.
            return redirect(logins.uninvitedPath(), null);
        }
        return redirect(pending.get().returnTo(),
                sessionCookies.issued(issued.get().rawToken(), issued.get().expiresAt()).toString());
    }

    private ResponseEntity<Void> redirect(String location, String sessionCookieHeader) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location)
                // state 쿠키는 한 번만 쓴다.
                .header(HttpHeaders.SET_COOKIE, stateCookie.expired().toString());
        if (sessionCookieHeader != null) {
            builder.header(HttpHeaders.SET_COOKIE, sessionCookieHeader);
        }
        return builder.build();
    }

    private String randomUrlSafe() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challengeOf(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("PKCE challenge를 만들 수 없다.");
        }
    }
}
