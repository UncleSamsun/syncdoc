package io.github.unclesamsun.syncdoc.dashboard.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    List<TaskEntity> findBySnapshotIdOrderByTaskSpecId(UUID snapshotId);

    @Transactional
    void deleteBySnapshotId(UUID snapshotId);
}
