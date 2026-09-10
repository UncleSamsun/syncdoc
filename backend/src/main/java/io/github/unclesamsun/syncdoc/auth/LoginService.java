package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.auth.domain.InvitationEntity;
import io.github.unclesamsun.syncdoc.auth.domain.InvitationRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.crypto.TokenCipher;
import io.github.unclesamsun.syncdoc.github.GitHubTokens;
import io.github.unclesamsun.syncdoc.github.GitHubUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신원 확인이 끝난 계정을 서비스 사용자로 만든다.
 * 허용 목록에 없으면 세션을 발급하지 않는다. GitHub 로그인 성공과 서비스 이용 허용은 다른 값이다.
 */
@Service
public class LoginService {

    private final UserRepository users;
    private final InvitationRepository invitations;
    private final UserCredentialRepository credentials;
    private final SessionService sessions;
    private final TokenCipher cipher;
    private final AuthProperties properties;
    private final Clock clock;

    public LoginService(UserRepository users, InvitationRepository invitations,
                        UserCredentialRepository credentials, SessionService sessions,
                        TokenCipher cipher, AuthProperties properties, Clock clock) {
        this.users = users;
        this.invitations = invitations;
        this.credentials = credentials;
        this.sessions = sessions;
        this.cipher = cipher;
        this.properties = properties;
        this.clock = clock;
    }

    /** 세션을 돌려주면 이용이 허용된 것이다. 빈 값은 미초대다. */
    @Transactional
    public Optional<SessionService.IssuedSession> completeLogin(GitHubUser identity, GitHubTokens tokens) {
        Instant now = clock.instant();
        ensureFirstAdminInvited(identity.githubUserId(), now);
        if (!isAllowed(identity.githubUserId())) {
            return Optional.empty();
        }
        UserEntity user = upsertUser(identity, now);
        storeCredentials(user.getId(), tokens);
        return Optional.of(sessions.issue(user.getId()));
    }

    /** 최초 관리자는 초대해 줄 사람이 없다. 배포 설정이 초대를 대신한다. */
    private void ensureFirstAdminInvited(String githubUserId, Instant now) {
        if (!sessions.isServiceAdmin(githubUserId)) {
            return;
        }
        InvitationEntity invitation = invitations.findByGithubUserId(githubUserId).orElse(null);
        if (invitation == null) {
            invitations.save(new InvitationEntity(UUID.randomUUID(), githubUserId, null, now));
        } else if (!invitation.isActive()) {
            invitation.regrant(null, now);
        }
    }

    private boolean isAllowed(String githubUserId) {
        return invitations.findByGithubUserId(githubUserId).map(InvitationEntity::isActive).orElse(false);
    }

    private UserEntity upsertUser(GitHubUser identity, Instant now) {
        return users.findByGithubUserId(identity.githubUserId())
                .map(existing -> {
                    existing.renameTo(identity.login(), now);
                    return existing;
                })
                .orElseGet(() -> users.save(new UserEntity(
                        UUID.randomUUID(), identity.githubUserId(), identity.login(), now, now)));
    }

    private void storeCredentials(UUID userId, GitHubTokens tokens) {
        String access = cipher.encrypt(tokens.accessToken());
        String refresh = tokens.refreshToken() == null ? null : cipher.encrypt(tokens.refreshToken());
        credentials.findById(userId).ifPresentOrElse(
                existing -> existing.replaceTokens(access, refresh, tokens.expiresAt(),
                        tokens.refreshExpiresAt(), cipher.keyVersion()),
                () -> credentials.save(new UserCredentialEntity(userId, access, refresh,
                        tokens.expiresAt(), tokens.refreshExpiresAt(), cipher.keyVersion())));
    }

    public String uninvitedPath() {
        return properties.uninvitedPath();
    }
}
