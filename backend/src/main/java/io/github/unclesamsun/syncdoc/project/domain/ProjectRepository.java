package io.github.unclesamsun.syncdoc.project.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    Optional<ProjectEntity> findByGithubRepositoryId(String githubRepositoryId);

    List<ProjectEntity> findAllByOrderByCreatedAtDesc();

    /**
     * 현재 게시본을 바꾼다. 연결 설정의 낙관적 잠금 version은 올리지 않는다.
     * 수집은 사용자가 고친 설정과 경쟁하는 변경이 아니며, 수집 때문에 사용자의 설정 저장이
     * 충돌로 거절되면 안 되기 때문이다. 다른 프로젝트의 snapshot은 복합 외래키가 막는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProjectEntity project set project.currentSnapshotId = :snapshotId where project.id = :id")
    int publishSnapshot(@Param("id") UUID id, @Param("snapshotId") UUID snapshotId);
}
