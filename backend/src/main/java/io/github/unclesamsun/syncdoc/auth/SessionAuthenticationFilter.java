package io.github.unclesamsun.syncdoc.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 세션 쿠키를 이번 요청의 사용자로 바꾼다. 쿠키가 없거나 모르는 값이면 아무것도 하지 않는다. */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    /** 원시 세션 토큰을 뒤 단계에 넘기는 request attribute 이름. */
    public static final String RAW_TOKEN_ATTRIBUTE = SessionAuthenticationFilter.class.getName() + ".rawToken";

    private final SessionService sessions;

    public SessionAuthenticationFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawToken = readCookie(request);
        if (rawToken != null) {
            sessions.authenticate(rawToken).ifPresent(user -> {
                request.setAttribute(CurrentUser.ATTRIBUTE, user);
                request.setAttribute(RAW_TOKEN_ATTRIBUTE, rawToken);
                List<SimpleGrantedAuthority> authorities = user.serviceAdmin()
                        ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                        : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
            });
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SessionService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
