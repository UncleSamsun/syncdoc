package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.common.ApiError;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * GitHub 자격증명이 죽은 요청을 모든 계약에서 같게 답한다.
 *
 * <p>계약마다 같은 처리를 적어 두면 한 곳을 빠뜨렸을 때 그 계약만 500을 낸다. 실제로 그렇게
 * 나가는 것을 2026-09-22에 확인했다.
 */
@RestControllerAdvice
public class ReauthAdvice {

    private final SessionService sessions;
    private final SessionCookies cookies;

    public ReauthAdvice(SessionService sessions, SessionCookies cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @ExceptionHandler(ReauthRequiredException.class)
    ResponseEntity<ApiError> reauthRequired(HttpServletRequest request) {
        // 자격증명 없이는 아무것도 읽을 수 없다. 세션을 남겨 두면 같은 실패를 되풀이한다.
        revokeSessionOf(request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookies.expired().toString())
                .body(ApiError.of("GITHUB_REAUTH_REQUIRED", "다시 로그인해 주세요.",
                        UUID.randomUUID().toString()));
    }

    @ExceptionHandler(UserCredentialService.MissingCredentialException.class)
    ResponseEntity<ApiError> missingCredential(HttpServletRequest request) {
        revokeSessionOf(request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookies.expired().toString())
                .body(ApiError.of("UNAUTHENTICATED", "다시 로그인해 주세요.", UUID.randomUUID().toString()));
    }

    private void revokeSessionOf(HttpServletRequest request) {
        Cookie[] all = request.getCookies();
        if (all == null) {
            return;
        }
        for (Cookie cookie : all) {
            if (SessionService.COOKIE_NAME.equals(cookie.getName())) {
                sessions.revoke(cookie.getValue());
            }
        }
    }
}
