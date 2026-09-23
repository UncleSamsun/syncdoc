package io.github.unclesamsun.syncdoc.dashboard.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    List<TaskEntity> findBySnapshotIdOrderByTaskSpecId(UUID snapshotId);

    @Transactional
    void deleteBySnapshotId(UUID snapshotId);

    /** 연결 해제(API-026). 프로젝트의 모든 게시본에 매인 행을 한 번에 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TaskEntity row where row.snapshotId in "
            + "(select snapshot.id from DocumentSnapshotEntity snapshot where snapshot.projectId = :projectId)")
    @Transactional
    void deleteByProjectId(@Param("projectId") UUID projectId);
}
