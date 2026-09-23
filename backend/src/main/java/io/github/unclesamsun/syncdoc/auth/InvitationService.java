package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.auth.domain.InvitationEntity;
import io.github.unclesamsun.syncdoc.auth.domain.InvitationRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.github.GitHubIdentityGateway;
import io.github.unclesamsun.syncdoc.github.GitHubUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 허용 목록 관리. 초대 취소는 그 사용자의 세션과 자격증명을 즉시 끊는다. */
@Service
public class InvitationService {

    private final InvitationRepository invitations;
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final SessionService sessions;
    private final GitHubIdentityGateway identity;
    private final Clock clock;

    public InvitationService(InvitationRepository invitations, UserRepository users,
                             UserCredentialRepository credentials, SessionService sessions,
                             GitHubIdentityGateway identity, Clock clock) {
        this.invitations = invitations;
        this.users = users;
        this.credentials = credentials;
        this.sessions = sessions;
        this.identity = identity;
        this.clock = clock;
    }

    public record InvitationView(UUID id, String githubUserId, boolean active, Instant grantedAt) {
    }

    /** 새로 만들었으면 created=true다. 같은 계정이면 기존 항목을 되살려 돌려준다. */
    public record InviteResult(InvitationView invitation, boolean created) {
    }

    @Transactional(readOnly = true)
    public List<InvitationView> list(int limit) {
        return invitations.findAllByOrderByGrantedAtDesc(PageRequest.of(0, limit))
                .map(InvitationService::toView)
                .toList();
    }

    /** 계정명은 바뀔 수 있으므로 GitHub에서 안정된 사용자 ID를 확인해 저장한다. */
    @Transactional
    public InviteResult invite(String githubLogin, CurrentUser actor) {
        GitHubUser user = identity.fetchUserByLogin(githubLogin);
        Instant now = clock.instant();
        InvitationEntity existing = invitations.findByGithubUserId(user.githubUserId()).orElse(null);
        if (existing != null) {
            if (!existing.isActive()) {
                existing.regrant(actor.id(), now);
            }
            return new InviteResult(toView(existing), false);
        }
        InvitationEntity created = invitations.save(
                new InvitationEntity(UUID.randomUUID(), user.githubUserId(), actor.id(), now));
        return new InviteResult(toView(created), true);
    }

    /** 최초 관리자는 취소할 수 없다. 그러면 아무도 초대를 관리할 수 없게 된다. */
    @Transactional
    public void revoke(UUID invitationId) {
        InvitationEntity invitation = invitations.findById(invitationId)
                .orElseThrow(InvitationNotFoundException::new);
        if (sessions.isServiceAdmin(invitation.getGithubUserId())) {
            throw new FirstAdminNotRevocableException();
        }
        invitation.revoke(clock.instant());
        users.findByGithubUserId(invitation.getGithubUserId()).ifPresent(user -> {
            sessions.revokeAllOf(user.getId());
            credentials.deleteByUserId(user.getId());
        });
    }

    private static InvitationView toView(InvitationEntity entity) {
        return new InvitationView(entity.getId(), entity.getGithubUserId(),
                entity.isActive(), entity.getGrantedAt());
    }

    public static class InvitationNotFoundException extends RuntimeException {
    }

    public static class FirstAdminNotRevocableException extends RuntimeException {
    }
}
