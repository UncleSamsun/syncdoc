package io.github.unclesamsun.syncdoc.document.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    List<DocumentEntity> findBySnapshotIdOrderByPath(UUID snapshotId);

    /**
     * 경로 순으로 한 쪽을 읽는다. cursor가 경로라서 정렬과 이어 읽기가 같은 기준을 쓴다.
     *
     * @param after 이 경로보다 뒤부터. 처음이면 빈 문자열
     */
    @Query(value = """
            select * from documents
            where snapshot_id = :snapshotId and path > :after
            order by path
            limit :limit
            """, nativeQuery = true)
    List<DocumentEntity> findPage(@Param("snapshotId") UUID snapshotId, @Param("after") String after,
                                  @Param("limit") int limit);

    long countBySnapshotId(UUID snapshotId);

    /** 실패해서 미완성으로 남은 게시본을 다시 채우기 전에 비운다. */
    @Transactional
    void deleteBySnapshotId(UUID snapshotId);

    /** 연결 해제(API-026). 프로젝트의 모든 게시본에 매인 행을 한 번에 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DocumentEntity row where row.snapshotId in "
            + "(select snapshot.id from DocumentSnapshotEntity snapshot where snapshot.projectId = :projectId)")
    @Transactional
    void deleteByProjectId(@Param("projectId") UUID projectId);
}
