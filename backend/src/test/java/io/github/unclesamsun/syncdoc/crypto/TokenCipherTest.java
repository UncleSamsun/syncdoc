package io.github.unclesamsun.syncdoc.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void round_trips_a_token() {
        TokenCipher cipher = new TokenCipher(KEY, 1);
        String stored = cipher.encrypt("gho_secret");
        assertThat(cipher.decrypt(stored)).isEqualTo("gho_secret");
    }

    @Test
    void the_stored_form_does_not_contain_the_plaintext() {
        TokenCipher cipher = new TokenCipher(KEY, 1);
        assertThat(cipher.encrypt("gho_secret")).doesNotContain("gho_secret");
    }

    @Test
    void the_same_plaintext_encrypts_differently_each_time() {
        TokenCipher cipher = new TokenCipher(KEY, 1);
        assertThat(cipher.encrypt("gho_secret")).isNotEqualTo(cipher.encrypt("gho_secret"));
    }

    @Test
    void a_tampered_value_is_rejected_instead_of_returning_garbage() {
        TokenCipher cipher = new TokenCipher(KEY, 1);
        String stored = cipher.encrypt("gho_secret");
        String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("A") ? "BB" : "AA");
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void without_a_key_it_fails_loudly_naming_the_missing_setting() {
        TokenCipher cipher = new TokenCipher("", 1);
        assertThatThrownBy(() -> cipher.encrypt("x"))
                .isInstanceOf(TokenCipherNotConfiguredException.class)
                .hasMessageContaining("SYNCDOC_TOKEN_KEY");
    }
}
