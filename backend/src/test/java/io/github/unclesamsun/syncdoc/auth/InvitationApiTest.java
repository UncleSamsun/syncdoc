package io.github.unclesamsun.syncdoc.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.auth.domain.InvitationRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.github.FakeGitHubIdentityGateway;
import io.github.unclesamsun.syncdoc.github.GitHubIdentityGateway;
import io.github.unclesamsun.syncdoc.github.GitHubUser;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@Import(InvitationApiTest.Identity.class)
@TestPropertySource(properties = "syncdoc.auth.admin-github-user-id=1")
class InvitationApiTest extends PostgresContainerSupport {

    @TestConfiguration
    static class Identity {

        @Bean
        @Primary
        GitHubIdentityGateway fakeIdentity() {
            return new FakeGitHubIdentityGateway();
        }
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserRepository users;
    @Autowired
    SessionService sessions;
    @Autowired
    InvitationRepository invitations;
    @Autowired
    UserCredentialRepository credentials;
    @Autowired
    GitHubIdentityGateway identity;

    private record Actor(String cookie, String csrf, UUID userId) {
    }

    private Actor actor(String githubUserId) {
        Instant now = Instant.now();
        UserEntity user = users.save(
                new UserEntity(UUID.randomUUID(), githubUserId, "u" + githubUserId, now, now));
        String raw = sessions.issue(user.getId()).rawToken();
        return new Actor(SessionService.COOKIE_NAME + "=" + raw, sessions.csrfTokenFor(raw), user.getId());
    }

    private HttpHeaders headers(Actor actor) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, actor.cookie());
        headers.add(CsrfTokenFilter.HEADER, actor.csrf());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private FakeGitHubIdentityGateway fakeIdentity() {
        return (FakeGitHubIdentityGateway) identity;
    }

    private ResponseEntity<String> invite(Actor admin, String login) {
        return rest.exchange("/api/v1/invitations", HttpMethod.POST,
                new HttpEntity<>("{\"githubLogin\":\"" + login + "\"}", headers(admin)), String.class);
    }

    @Test
    void inviting_the_same_login_twice_returns_the_existing_entry() {
        Actor admin = actor("1");
        fakeIdentity().registerLogin("teammate", new GitHubUser("777", "teammate"));

        assertThat(invite(admin, "teammate").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(invite(admin, "teammate").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(invitations.findByGithubUserId("777")).isPresent();
        assertThat(invitations.findAll()).hasSize(1);
    }

    @Test
    void an_invitation_stores_the_github_user_id_not_the_login() {
        Actor admin = actor("1");
        fakeIdentity().registerLogin("teammate", new GitHubUser("777", "teammate"));

        assertThat(invite(admin, "teammate").getBody()).contains("\"githubUserId\":\"777\"");
    }

    @Test
    void revoking_an_invitation_drops_the_sessions_and_credentials_of_that_user() {
        Actor admin = actor("1");
        Actor member = actor("777");
        credentials.save(new UserCredentialEntity(member.userId(), "cipher", null, null, null, 1));
        fakeIdentity().registerLogin("teammate", new GitHubUser("777", "teammate"));
        invite(admin, "teammate");
        UUID invitationId = invitations.findByGithubUserId("777").orElseThrow().getId();

        HttpHeaders memberHeaders = new HttpHeaders();
        memberHeaders.add(HttpHeaders.COOKIE, member.cookie());
        assertThat(rest.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(memberHeaders), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> revoked = rest.exchange("/api/v1/invitations/" + invitationId,
                HttpMethod.DELETE, new HttpEntity<>(headers(admin)), String.class);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(rest.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(memberHeaders), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(credentials.findById(member.userId())).isEmpty();
    }

    @Test
    void the_first_admin_cannot_be_revoked() {
        Actor admin = actor("1");
        fakeIdentity().registerLogin("boss", new GitHubUser("1", "boss"));
        invite(admin, "boss");
        UUID invitationId = invitations.findByGithubUserId("1").orElseThrow().getId();

        ResponseEntity<String> response = rest.exchange("/api/v1/invitations/" + invitationId,
                HttpMethod.DELETE, new HttpEntity<>(headers(admin)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void an_invitation_without_the_csrf_header_is_rejected() {
        Actor admin = actor("1");
        fakeIdentity().registerLogin("teammate", new GitHubUser("777", "teammate"));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, admin.cookie());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = rest.exchange("/api/v1/invitations", HttpMethod.POST,
                new HttpEntity<>("{\"githubLogin\":\"teammate\"}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(invitations.findByGithubUserId("777")).isEmpty();
    }
}
