package io.github.unclesamsun.syncdoc.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 상태를 바꾸는 요청에 `X-CSRF-Token` 헤더를 요구한다. 값은 API-003이 준 것과 같아야 한다.
 * 세션이 없는 요청은 이 필터가 막지 않는다. 인증 실패는 뒤의 진입점이 401로 답한다.
 */
public class CsrfTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-CSRF-Token";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final SessionService sessions;

    public CsrfTokenFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawToken = (String) request.getAttribute(SessionAuthenticationFilter.RAW_TOKEN_ATTRIBUTE);
        if (rawToken == null || SAFE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String presented = request.getHeader(HEADER);
        String expected = sessions.csrfTokenFor(rawToken);
        if (presented == null || !MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"CSRF_TOKEN_INVALID\",\"message\":\"요청을 확인할 수 없습니다.\","
                    + "\"requestId\":\"" + UUID.randomUUID() + "\",\"details\":{}}");
            return;
        }
        chain.doFilter(request, response);
    }
}
