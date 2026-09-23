package io.github.unclesamsun.syncdoc.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * App JWT 서명. GitHub가 내려주는 키는 PKCS#1이고 자바는 PKCS#8만 읽으므로 두 형식을 모두 확인한다.
 * 이 경로가 막히면 설치 토큰을 못 받고 수집 전체가 서지 않는다.
 */
class AppPrivateKeyTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    private boolean verifies(String jwt) throws Exception {
        int lastDot = jwt.lastIndexOf('.');
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(jwt.substring(0, lastDot).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(jwt.substring(lastDot + 1)));
    }

    private static String payloadOf(String jwt) {
        String[] parts = jwt.split("\\.");
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    @Test
    void a_pkcs8_key_signs_a_verifiable_token() throws Exception {
        AppPrivateKey key = AppPrivateKey.parse(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));

        String jwt = key.jwt("4895456", Instant.parse("2026-09-11T00:00:00Z"));

        assertThat(verifies(jwt)).isTrue();
        assertThat(payloadOf(jwt)).contains("\"iss\":\"4895456\"");
    }

    @Test
    void a_pkcs1_key_from_github_signs_the_same_way() throws Exception {
        AppPrivateKey key = AppPrivateKey.parse(pem("RSA PRIVATE KEY", pkcs1Of(keyPair)));

        assertThat(verifies(key.jwt("4895456", Instant.now()))).isTrue();
    }

    @Test
    void an_escaped_newline_key_from_an_environment_variable_still_parses() throws Exception {
        String oneLine = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()).replace("\n", "\\n");

        assertThat(verifies(AppPrivateKey.parse(oneLine).jwt("4895456", Instant.now()))).isTrue();
    }

    @Test
    void the_token_is_short_lived_and_backdated_for_clock_skew() {
        Instant now = Instant.parse("2026-09-11T00:00:00Z");

        String payload = payloadOf(
                AppPrivateKey.parse(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded())).jwt("1", now));

        assertThat(payload).contains("\"iat\":" + (now.getEpochSecond() - 60));
        // GitHub는 10분을 넘는 App JWT를 거절한다.
        assertThat(payload).contains("\"exp\":" + (now.getEpochSecond() + 540));
    }

    @Test
    void a_key_that_is_not_a_key_fails_with_a_clear_message() {
        assertThatThrownBy(() -> AppPrivateKey.parse("-----BEGIN PRIVATE KEY-----\n!!!\n-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private key");
    }

    /** GitHub가 내려주는 형식이다. SEQUENCE {version, n, e, d, p, q, dp, dq, qInv} */
    private static byte[] pkcs1Of(KeyPair pair) {
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) pair.getPrivate();
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(integer(BigInteger.ZERO));
        content.writeBytes(integer(key.getModulus()));
        content.writeBytes(integer(key.getPublicExponent()));
        content.writeBytes(integer(key.getPrivateExponent()));
        content.writeBytes(integer(key.getPrimeP()));
        content.writeBytes(integer(key.getPrimeQ()));
        content.writeBytes(integer(key.getPrimeExponentP()));
        content.writeBytes(integer(key.getPrimeExponentQ()));
        content.writeBytes(integer(key.getCrtCoefficient()));
        return element((byte) 0x30, content.toByteArray());
    }

    private static byte[] integer(BigInteger value) {
        return element((byte) 0x02, value.toByteArray());
    }

    private static byte[] element(byte tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (content.length < 0x80) {
            out.write(content.length);
        } else {
            byte[] size = BigInteger.valueOf(content.length).toByteArray();
            byte[] trimmed = size[0] == 0 ? java.util.Arrays.copyOfRange(size, 1, size.length) : size;
            out.write(0x80 | trimmed.length);
            out.writeBytes(trimmed);
        }
        out.writeBytes(content);
        return out.toByteArray();
    }
}
