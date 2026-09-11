package io.github.unclesamsun.syncdoc.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.project.domain.InstallationEntity;
import io.github.unclesamsun.syncdoc.project.domain.InstallationRepository;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ProjectSchemaTest extends PostgresContainerSupport {

    @Autowired
    ProjectRepository projects;
    @Autowired
    InstallationRepository installations;
    @Autowired
    UserRepository users;

    private UUID installation() {
        Instant now = Instant.now();
        return installations.save(new InstallationEntity(
                UUID.randomUUID(), "inst-" + UUID.randomUUID(), "79427050", "active", now)).getId();
    }

    private UUID user() {
        Instant now = Instant.now();
        return users.save(new UserEntity(UUID.randomUUID(), "u" + UUID.randomUUID(), "someone", now, now)).getId();
    }

    private ProjectEntity newProject(String githubRepositoryId) {
        return new ProjectEntity(UUID.randomUUID(), githubRepositoryId, "o/r", installation(), user(),
                "main", "docs", null, Instant.now());
    }

    @Test
    void a_project_is_found_by_the_stable_repository_id() {
        projects.save(newProject("101"));
        assertThat(projects.findByGithubRepositoryId("101")).isPresent();
        assertThat(projects.findByGithubRepositoryId("o/r")).isEmpty();
    }

    @Test
    void the_same_repository_cannot_be_connected_twice() {
        projects.save(newProject("101"));
        assertThatThrownBy(() -> projects.saveAndFlush(newProject("101")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reconfiguring_raises_the_version_so_concurrent_edits_collide() {
        ProjectEntity saved = projects.saveAndFlush(newProject("102"));
        long before = saved.getVersion();

        saved.reconfigure("dev", "documents", null);
        long after = projects.saveAndFlush(saved).getVersion();

        assertThat(after).isGreaterThan(before);
    }

    @Test
    void a_new_project_has_no_snapshot_yet() {
        assertThat(projects.saveAndFlush(newProject("103")).getCurrentSnapshotId()).isNull();
    }
}
