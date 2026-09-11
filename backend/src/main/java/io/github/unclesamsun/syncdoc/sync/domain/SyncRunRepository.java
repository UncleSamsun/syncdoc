package io.github.unclesamsun.syncdoc.sync.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRunEntity, UUID> {

    Optional<SyncRunEntity> findFirstByProjectIdOrderByStartedAtDesc(UUID projectId);

    Optional<SyncRunEntity> findFirstByProjectIdAndOutcomeOrderByFinishedAtDesc(UUID projectId, String outcome);
}
