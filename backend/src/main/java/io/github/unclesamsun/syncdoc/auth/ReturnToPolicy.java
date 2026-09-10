package io.github.unclesamsun.syncdoc.auth;

/** returnTo는 서비스 내부 경로만 허용한다. 외부 주소로 되돌려 보내지 않는다. */
public final class ReturnToPolicy {

    public static final String DEFAULT = "/";

    private ReturnToPolicy() {
    }

    public static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return DEFAULT;
        }
        // `//host`와 `/\host`는 브라우저가 외부 주소로 읽는다. 스킴이 있는 값과 헤더 주입도 거절한다.
        if (!candidate.startsWith("/") || candidate.startsWith("//") || candidate.startsWith("/\\")
                || candidate.contains("://") || candidate.contains("\n") || candidate.contains("\r")) {
            return DEFAULT;
        }
        return candidate;
    }
}
