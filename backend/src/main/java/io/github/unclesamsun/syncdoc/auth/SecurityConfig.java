package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.common.ApiPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * API 계약 `## 공통`의 접근 조건. 세션 없이 부를 수 있는 계약은 로그인 시작·콜백(API-001·002),
 * webhook(API-021), 상태 확인(API-022·023)뿐이다. 관리자만 부를 수 있는 계약은 API-005·006·007이다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String[] PUBLIC_PATHS = {
            ApiPaths.BASE + "/health/live",
            ApiPaths.BASE + "/health/ready",
            ApiPaths.BASE + "/auth/github/start",
            ApiPaths.BASE + "/auth/github/callback",
            ApiPaths.BASE + "/webhooks/github",
    };

    /**
     * 처리되지 않은 예외는 서블릿이 `/error`로 포워딩한다. 이 경로가 막혀 있으면
     * 공개 경로에서 난 500이 401로 바뀌어 원인을 가린다.
     */
    private static final String ERROR_PATH = "/error";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionService sessions) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(ERROR_PATH).permitAll()
                        .requestMatchers(ApiPaths.BASE + "/invitations", ApiPaths.BASE + "/invitations/**")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 계약이 정한 `X-CSRF-Token` 헤더 검사는 CsrfTokenFilter가 한다. 두 규칙을 겹치지 않는다.
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new SessionAuthenticationFilter(sessions), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfTokenFilter(sessions), SessionAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
