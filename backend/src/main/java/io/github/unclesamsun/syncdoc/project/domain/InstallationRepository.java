package io.github.unclesamsun.syncdoc.project.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallationRepository extends JpaRepository<InstallationEntity, UUID> {

    Optional<InstallationEntity> findByGithubInstallationId(String githubInstallationId);
}
