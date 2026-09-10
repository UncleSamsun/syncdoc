package io.github.unclesamsun.syncdoc.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OAuth 토큰을 AES-GCM으로 암복호한다. 저장 형태는 base64(iv || ciphertext+tag)다.
 * 키는 DB 밖 설정에서 읽는다. 키가 없으면 조용히 평문을 두지 않고 실패한다.
 */
@Component
public class TokenCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final byte[] key;
    private final int keyVersion;

    public TokenCipher(@Value("${syncdoc.crypto.token-key:}") String base64Key,
                       @Value("${syncdoc.crypto.key-version:1}") int keyVersion) {
        this.key = decodeKey(base64Key);
        this.keyVersion = keyVersion;
    }

    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException("SYNCDOC_TOKEN_KEY는 base64로 인코딩한 32바이트여야 한다.");
        }
        return decoded;
    }

    public int keyVersion() {
        return keyVersion;
    }

    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        byte[] ciphertext = run(Cipher.ENCRYPT_MODE, iv, plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(out);
    }

    public String decrypt(String stored) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(stored);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("저장된 토큰을 읽을 수 없다.");
        }
        if (raw.length <= IV_BYTES) {
            throw new IllegalStateException("저장된 토큰을 읽을 수 없다.");
        }
        byte[] iv = new byte[IV_BYTES];
        byte[] ciphertext = new byte[raw.length - IV_BYTES];
        System.arraycopy(raw, 0, iv, 0, IV_BYTES);
        System.arraycopy(raw, IV_BYTES, ciphertext, 0, ciphertext.length);
        return new String(run(Cipher.DECRYPT_MODE, iv, ciphertext), StandardCharsets.UTF_8);
    }

    private byte[] run(int mode, byte[] iv, byte[] input) {
        if (key == null) {
            throw new TokenCipherNotConfiguredException();
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(input);
        } catch (TokenCipherNotConfiguredException e) {
            throw e;
        } catch (Exception e) {
            // 예외 메시지에 평문이나 키를 담지 않는다.
            throw new IllegalStateException("토큰 암복호에 실패했다.");
        }
    }
}
