package io.github.unclesamsun.syncdoc.support;

import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.project.domain.InstallationEntity;
import io.github.unclesamsun.syncdoc.project.domain.InstallationRepository;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import java.time.Instant;
import java.util.UUID;

/** 연결된 프로젝트 하나를 만드는 준비 코드. 수집 테스트마다 같은 세 테이블을 채우지 않으려고 모았다. */
public class ProjectFixture {

    private final ProjectRepository projects;
    private final InstallationRepository installations;
    private final UserRepository users;

    public ProjectFixture(ProjectRepository projects, InstallationRepository installations,
                          UserRepository users) {
        this.projects = projects;
        this.installations = installations;
        this.users = users;
    }

    public ProjectEntity newProject(String githubRepositoryId) {
        return newProject(githubRepositoryId, null);
    }

    /** @param createdBy 연결자로 둘 사용자. null이면 이 fixture가 새 사용자를 만든다 */
    public ProjectEntity newProject(String githubRepositoryId, UUID createdBy) {
        Instant now = Instant.now();
        UUID installation = installations.save(new InstallationEntity(UUID.randomUUID(),
                "inst-" + UUID.randomUUID(), "79427050", "active", now)).getId();
        UUID user = createdBy != null ? createdBy : users.save(new UserEntity(UUID.randomUUID(),
                "u-" + UUID.randomUUID(), "someone", now, now)).getId();
        return projects.saveAndFlush(new ProjectEntity(UUID.randomUUID(), githubRepositoryId,
                "owner/" + githubRepositoryId, installation, user, "main", "docs", null, now));
    }
}
