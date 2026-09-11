package io.github.unclesamsun.syncdoc.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentSnapshotRepository extends JpaRepository<DocumentSnapshotEntity, UUID> {

    Optional<DocumentSnapshotEntity> findByProjectIdAndSourceRevisionAndRendererVersionAndPolicyVersion(
            UUID projectId, String sourceRevision, String rendererVersion, String policyVersion);

    List<DocumentSnapshotEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
