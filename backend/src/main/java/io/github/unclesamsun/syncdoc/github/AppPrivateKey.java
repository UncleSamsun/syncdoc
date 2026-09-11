package io.github.unclesamsun.syncdoc.github;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

/**
 * GitHub App의 private key로 App JWT를 만든다.
 *
 * <p>GitHub가 내려주는 키는 PKCS#1(`BEGIN RSA PRIVATE KEY`)이고 자바 표준 {@code KeyFactory}는
 * PKCS#8만 읽는다. 그래서 PKCS#1 본문을 PKCS#8 구조로 감싸 준다. JWT 라이브러리를 더하는 대신
 * 이 30여 줄을 두는 쪽을 택했다. 서명 알고리즘은 GitHub가 요구하는 RS256 하나뿐이다.
 *
 * <p>키 자체와 만들어진 JWT는 로그·응답·예외 메시지에 넣지 않는다.
 */
public final class AppPrivateKey {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    private final PrivateKey key;

    private AppPrivateKey(PrivateKey key) {
        this.key = key;
    }

    public static AppPrivateKey parse(String pem) {
        String body = pem.replace("\\n", "\n");
        boolean pkcs1 = body.contains("BEGIN RSA PRIVATE KEY");
        String base64 = body.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("GitHub App private key를 읽을 수 없다. PEM 형식이 아니다.");
        }
        try {
            byte[] pkcs8 = pkcs1 ? wrapAsPkcs8(der) : der;
            return new AppPrivateKey(KeyFactory.getInstance("RSA")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pkcs8)));
        } catch (Exception e) {
            throw new IllegalStateException("GitHub App private key를 읽을 수 없다. RSA 키가 맞는지 확인한다.");
        }
    }

    /**
     * App JWT를 만든다. GitHub는 최대 10분까지 허용하고 시계 차이를 감안해 발급 시각을 조금 앞당긴다.
     *
     * @param appId GitHub App ID
     */
    public String jwt(String appId, java.time.Instant now) {
        long issuedAt = now.getEpochSecond() - 60;
        long expiresAt = now.getEpochSecond() + 540;
        String header = URL.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = URL.encodeToString(("{\"iat\":" + issuedAt + ",\"exp\":" + expiresAt
                + ",\"iss\":\"" + appId + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + URL.encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("App JWT에 서명하지 못했다.");
        }
    }

    /** SEQUENCE { INTEGER 0, SEQUENCE { OID rsaEncryption, NULL }, OCTET STRING(키) } */
    private static byte[] wrapAsPkcs8(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algorithm = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7,
                0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octetString = derElement((byte) 0x04, pkcs1);
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(version);
        content.writeBytes(algorithm);
        content.writeBytes(octetString);
        return derElement((byte) 0x30, content.toByteArray());
    }

    private static byte[] derElement(byte tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = content.length;
        if (length < 0x80) {
            out.write(length);
        } else {
            byte[] size = java.math.BigInteger.valueOf(length).toByteArray();
            byte[] trimmed = size[0] == 0 ? java.util.Arrays.copyOfRange(size, 1, size.length) : size;
            out.write(0x80 | trimmed.length);
            out.writeBytes(trimmed);
        }
        out.writeBytes(content);
        return out.toByteArray();
    }
}
