package io.github.unclesamsun.syncdoc.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 미로그인 요청에 계약의 JSON 401을 돌려준다. 로그인 화면으로 redirect하지 않는다. */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String requestId = UUID.randomUUID().toString();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\","
                        + "\"requestId\":\"" + requestId + "\",\"details\":{}}");
    }
}
