package io.github.unclesamsun.syncdoc.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class IdentitySchemaTest extends PostgresContainerSupport {

    @Autowired
    UserRepository users;

    @Test
    void a_user_is_found_by_github_user_id_not_by_login() {
        users.save(newUser("583231", "octocat"));
        assertThat(users.findByGithubUserId("583231")).isPresent();
        assertThat(users.findByGithubUserId("octocat")).isEmpty();
    }

    @Test
    void the_same_github_user_id_cannot_be_stored_twice() {
        users.save(newUser("100", "a"));
        assertThatThrownBy(() -> users.saveAndFlush(newUser("100", "b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserEntity newUser(String githubUserId, String login) {
        Instant now = Instant.now();
        return new UserEntity(UUID.randomUUID(), githubUserId, login, now, now);
    }
}
