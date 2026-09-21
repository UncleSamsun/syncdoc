package io.github.unclesamsun.syncdoc.dashboard.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface IssueSnapshotRepository extends JpaRepository<IssueSnapshotEntity, UUID> {

    List<IssueSnapshotEntity> findByProjectId(UUID projectId);

    @Transactional
    void deleteByProjectId(UUID projectId);
}
