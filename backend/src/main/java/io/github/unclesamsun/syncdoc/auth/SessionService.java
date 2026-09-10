package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.auth.domain.SessionEntity;
import io.github.unclesamsun.syncdoc.auth.domain.SessionRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 발급과 검증. 쿠키에는 불투명 난수를 담고 DB에는 그 SHA-256 해시만 남긴다.
 * CSRF 토큰은 원시 토큰의 HMAC이라 쿠키를 읽을 수 없는 교차 출처 요청은 만들 수 없다.
 */
@Service
public class SessionService {

    public static final String COOKIE_NAME = "SYNCDOC_SESSION";

    private final SessionRepository sessions;
    private final UserRepository users;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SessionService(SessionRepository sessions, UserRepository users, AuthProperties properties, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    public record IssuedSession(String rawToken, Instant expiresAt) {
    }

    @Transactional
    public IssuedSession issue(UUID userId) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.sessionTtl());
        sessions.save(new SessionEntity(UUID.randomUUID(), userId, hash(rawToken), expiresAt, now));
        return new IssuedSession(rawToken, expiresAt);
    }

    @Transactional
    public Optional<CurrentUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<SessionEntity> found = sessions.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SessionEntity session = found.get();
        if (session.isExpiredAt(clock.instant())) {
            sessions.delete(session);
            return Optional.empty();
        }
        return users.findById(session.getUserId())
                .map(user -> new CurrentUser(user.getId(), user.getGithubUserId(), user.getLogin(),
                        isServiceAdmin(user.getGithubUserId())));
    }

    public boolean isServiceAdmin(String githubUserId) {
        String admin = properties.adminGithubUserId();
        return admin != null && !admin.isBlank() && admin.equals(githubUserId);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            sessions.deleteByTokenHash(hash(rawToken));
        }
    }

    /** 초대 취소처럼 사용자 단위로 끊어야 할 때 쓴다. */
    @Transactional
    public void revokeAllOf(UUID userId) {
        sessions.deleteByUserId(userId);
    }

    public String csrfTokenFor(String rawToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(csrfKeyBytes(), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CSRF 토큰을 만들 수 없다.");
        }
    }

    private byte[] csrfKeyBytes() {
        String key = properties.csrfKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("CSRF 키가 없다. SYNCDOC_CSRF_KEY를 설정해야 한다.");
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder()
                    .encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("세션 토큰을 해시할 수 없다.");
        }
    }
}
