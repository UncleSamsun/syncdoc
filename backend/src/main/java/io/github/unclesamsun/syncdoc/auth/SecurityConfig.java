package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.common.ApiPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API 계약 `## 공통`의 접근 조건. 세션 없이 부를 수 있는 계약은 로그인 시작·콜백(API-001·002),
 * webhook(API-021), 상태 확인(API-022·023)뿐이다. 로그인·세션·CSRF 토큰 검사는 TASK-002가 더한다.
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

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new JsonAuthenticationEntryPoint()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
