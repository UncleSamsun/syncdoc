package io.github.unclesamsun.syncdoc.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** External application prefix. The ingress strips this before forwarding API requests. */
@Component
public class PublicPath {
    private final String prefix;
    public PublicPath(@Value("${syncdoc.public-base-path:}") String prefix) {
        if (!prefix.isEmpty() && !prefix.matches("(/[A-Za-z0-9_-]+)+")) {
            throw new IllegalArgumentException("public base path must be empty or an absolute path without a trailing slash");
        }
        this.prefix = prefix;
    }
    public String resolve(String path) {
        String safe = ReturnToPolicy.sanitize(path);
        String pathname = safe.split("[?#]", 2)[0];
        // Do not let browser URL normalization escape the configured application prefix.
        if (pathname.matches("(?i).*%2[eEfF].*") || pathname.matches("(?i).*%5c.*")
                || pathname.contains("\\") || pathname.matches(".*(?:^|/)\\.{1,2}(?:/|$).*")) {
            safe = "/";
        }
        return prefix + safe;
    }
}
