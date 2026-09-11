package io.github.unclesamsun.syncdoc.sync.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SyncJobRepository extends JpaRepository<SyncJobEntity, UUID> {

    Optional<SyncJobEntity> findFirstByProjectIdAndKindAndStateIn(
            UUID projectId, String kind, Collection<String> states);

    /**
     * 작업 행을 잠그고 읽는다. 게시는 임대 확인과 같은 트랜잭션에서 해야 하고, 그 사이에
     * 다른 worker가 작업을 가져가 임대 token을 바꾸는 것을 이 잠금이 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from SyncJobEntity job where job.id = :id")
    Optional<SyncJobEntity> findAndLockById(@Param("id") UUID id);

    List<SyncJobEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /**
     * 실행할 작업 하나를 잠근다. {@code skip locked}라 두 worker가 같은 작업을 잡지 못하고,
     * 서로 다른 작업이 있으면 기다리지 않고 각자 가져간다.
     */
    @Query(value = """
            select id from sync_jobs
            where state = 'queued' and due_at <= :now
            order by due_at
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<UUID> lockNextDue(@Param("now") Instant now);

    /**
     * 임대가 끊긴 작업을 잠근다. worker가 죽거나 서버가 재시작해도 이 경로로 다시 실행된다.
     * 오래된 worker가 나중에 돌아와도 임대 token이 달라져 결과를 남기지 못한다.
     */
    @Query(value = """
            select id from sync_jobs
            where state = 'running' and lease_until < :now
            order by lease_until
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<UUID> lockExpiredLease(@Param("now") Instant now);
}
