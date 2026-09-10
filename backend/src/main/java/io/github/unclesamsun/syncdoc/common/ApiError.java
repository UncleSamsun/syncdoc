package io.github.unclesamsun.syncdoc.common;

import java.util.Map;

/** API 계약 `## 공통`의 오류 형식. details에 비공개 이름이나 토큰을 넣지 않는다. */
public record ApiError(String code, String message, String requestId, Map<String, Object> details) {

    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(code, message, requestId, Map.of());
    }
}
