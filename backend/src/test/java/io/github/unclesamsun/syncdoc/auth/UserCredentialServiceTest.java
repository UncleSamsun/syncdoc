package io.github.unclesamsun.syncdoc.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.crypto.TokenCipher;
import io.github.unclesamsun.syncdoc.github.GitHubOAuthGateway;
import io.github.unclesamsun.syncdoc.github.GitHubTokens;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 저장소를 쓰되 시계와 OAuth 게이트웨이만 바꿔 끼운다.
 * 시계 빈을 통째로 고정하면 세션 만료 같은 다른 동작까지 함께 멈춘다.
 */
class UserCredentialServiceTest extends PostgresContainerSupport {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");

    @Autowired
    UserCredentialRepository credentials;
    @Autowired
    UserRepository users;
    @Autowired
    TokenCipher cipher;
    @Autowired
    TransactionTemplate transactions;

    private RecordingOAuth oauth;
    private UserCredentialService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        oauth = new RecordingOAuth();
        service = new UserCredentialService(credentials, cipher, oauth, Clock.fixed(NOW, ZoneOffset.UTC));
        userId = users.save(new UserEntity(UUID.randomUUID(), "900001", "holder", NOW, NOW)).getId();
    }

    private void store(String access, String refresh, Instant expiresAt) {
        credentials.save(new UserCredentialEntity(userId, cipher.encrypt(access),
                refresh == null ? null : cipher.encrypt(refresh), expiresAt, null, 1));
    }

    private String accessToken() {
        return transactions.execute(status -> service.accessTokenFor(userId));
    }

    @Test
    void a_token_with_time_left_is_returned_as_is() {
        store("gho_live", "ghr_1", NOW.plusSeconds(3600));
        assertThat(accessToken()).isEqualTo("gho_live");
        assertThat(oauth.refreshCalls).isZero();
    }

    @Test
    void a_token_about_to_expire_is_refreshed_and_stored() {
        store("gho_old", "ghr_1", NOW.plusSeconds(30));

        assertThat(accessToken()).isEqualTo("gho_new");
        assertThat(oauth.refreshCalls).isOne();
        assertThat(oauth.lastRefreshToken).isEqualTo("ghr_1");

        // 갱신 결과를 저장했으므로 다시 부를 때 또 갱신하지 않는다.
        assertThat(accessToken()).isEqualTo("gho_new");
        assertThat(oauth.refreshCalls).isOne();
    }

    @Test
    void the_refreshed_token_is_stored_encrypted_not_in_plain_text() {
        store("gho_old", "ghr_1", NOW.plusSeconds(30));
        accessToken();

        UserCredentialEntity stored = credentials.findById(userId).orElseThrow();
        assertThat(stored.getAccessTokenCiphertext()).doesNotContain("gho_new");
        assertThat(cipher.decrypt(stored.getAccessTokenCiphertext())).isEqualTo("gho_new");
    }

    @Test
    void a_token_without_an_expiry_is_never_refreshed() {
        store("gho_forever", "ghr_1", null);
        assertThat(accessToken()).isEqualTo("gho_forever");
        assertThat(oauth.refreshCalls).isZero();
    }

    @Test
    void a_token_without_a_refresh_token_cannot_be_refreshed() {
        store("gho_old", null, NOW.plusSeconds(30));
        assertThat(accessToken()).isEqualTo("gho_old");
        assertThat(oauth.refreshCalls).isZero();
    }

    @Test
    void a_missing_credential_is_reported_not_guessed() {
        assertThatThrownBy(this::accessToken)
                .isInstanceOf(UserCredentialService.MissingCredentialException.class);
    }

    static final class RecordingOAuth implements GitHubOAuthGateway {
        int refreshCalls;
        String lastRefreshToken;

        @Override
        public String authorizeUrl(String state, String codeChallenge) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GitHubTokens exchangeCode(String code, String codeVerifier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GitHubTokens refreshTokens(String refreshToken) {
            refreshCalls++;
            lastRefreshToken = refreshToken;
            return new GitHubTokens("gho_new", "ghr_2", NOW.plusSeconds(28800), null);
        }
    }
}
