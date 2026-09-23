package io.github.unclesamsun.syncdoc.sync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * GitHub webhook 서명 검사. 받은 raw body 그대로 계산해야 하며 JSON으로 바꿨다가 되돌린 값으로는
 * 서명이 맞지 않는다.
 *
 * <p>비교는 길이·내용이 달라도 같은 시간이 걸리는 방식으로 한다.
 */
public final class WebhookSignature {

    private WebhookSignature() {
    }

    public static boolean matches(String secret, byte[] rawBody, String presented) {
        if (presented == null || !presented.startsWith("sha256=")) {
            return false;
        }
        String expected = "sha256=" + hmacSha256(secret, rawBody);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    static String hmacSha256(String secret, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception e) {
            throw new IllegalStateException("서명을 계산하지 못했다", e);
        }
    }
}
