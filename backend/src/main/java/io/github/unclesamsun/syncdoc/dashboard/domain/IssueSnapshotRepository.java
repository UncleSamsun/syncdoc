package io.github.unclesamsun.syncdoc.dashboard.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IssueSnapshotRepository extends JpaRepository<IssueSnapshotEntity, UUID> {

    List<IssueSnapshotEntity> findByProjectId(UUID projectId);

    /**
     * 다시 읽기 전에 비운다.
     *
     * <p>파생 삭제(`deleteByProjectId`)를 쓰면 지우기가 새로 넣기보다 **뒤에** 반영된다. 그러면 두 번째
     * 수집부터 같은 Issue가 이미 있다며 유일 제약에 걸린다. 실제 저장소로 돌려 보고 잡은 문제라
     * 즉시 실행되는 일괄 삭제로 바꿨다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IssueSnapshotEntity issue where issue.projectId = :projectId")
    @Transactional
    void clearProject(@Param("projectId") UUID projectId);
}
