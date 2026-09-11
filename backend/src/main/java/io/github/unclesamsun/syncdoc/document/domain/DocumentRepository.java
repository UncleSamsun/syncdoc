package io.github.unclesamsun.syncdoc.document.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    List<DocumentEntity> findBySnapshotIdOrderByPath(UUID snapshotId);

    long countBySnapshotId(UUID snapshotId);

    /** 실패해서 미완성으로 남은 게시본을 다시 채우기 전에 비운다. */
    @Transactional
    void deleteBySnapshotId(UUID snapshotId);
}
