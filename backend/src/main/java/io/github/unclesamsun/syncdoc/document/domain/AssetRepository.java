package io.github.unclesamsun.syncdoc.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AssetRepository extends JpaRepository<AssetEntity, UUID> {

    List<AssetEntity> findBySnapshotIdOrderByPath(UUID snapshotId);

    Optional<AssetEntity> findByIdAndSnapshotId(UUID id, UUID snapshotId);

    /** 실패해서 미완성으로 남은 게시본을 다시 채우기 전에 비운다. */
    @Transactional
    void deleteBySnapshotId(UUID snapshotId);

    /** 연결 해제(API-026). 프로젝트의 모든 게시본에 매인 행을 한 번에 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AssetEntity row where row.snapshotId in "
            + "(select snapshot.id from DocumentSnapshotEntity snapshot where snapshot.projectId = :projectId)")
    @Transactional
    void deleteByProjectId(@Param("projectId") UUID projectId);
}
