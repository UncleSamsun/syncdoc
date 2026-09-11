package io.github.unclesamsun.syncdoc.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 수집 작업 하나. 활성 작업은 프로젝트·종류마다 하나뿐이고 그 제약은 DB의 partial unique가 지킨다.
 *
 * <p>임대 token이 소유의 증거다. worker는 자기가 받은 token으로만 결과를 남길 수 있고,
 * 임대가 만료되어 다른 worker가 같은 작업을 다시 잡으면 오래된 worker의 완료는 거부된다.
 */
@Entity
@Table(name = "sync_jobs")
public class SyncJobEntity {

    /** 문서 수집. 지금은 한 종류뿐이지만 활성 제약이 종류별이라 값으로 남긴다. */
    public static final String KIND_COLLECT = "collect";

    public static final String QUEUED = "queued";
    public static final String RUNNING = "running";
    public static final String SUCCEEDED = "succeeded";
    public static final String FAILED = "failed";
    public static final String CANCELED = "canceled";

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private String kind;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "target_revision")
    private String targetRevision;

    @Column(name = "rerun_requested", nullable = false)
    private boolean rerunRequested;

    @Column(name = "last_error_code")
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SyncJobEntity() {
    }

    private SyncJobEntity(UUID id, UUID projectId, String kind, Instant dueAt, Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.kind = kind;
        this.state = QUEUED;
        this.attempt = 0;
        this.dueAt = dueAt;
        this.rerunRequested = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SyncJobEntity queued(UUID projectId, Instant dueAt, Instant now) {
        return new SyncJobEntity(UUID.randomUUID(), projectId, KIND_COLLECT, dueAt, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getKind() {
        return kind;
    }

    public String getState() {
        return state;
    }

    public int getAttempt() {
        return attempt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public UUID getLeaseToken() {
        return leaseToken;
    }

    public String getTargetRevision() {
        return targetRevision;
    }

    public boolean isRerunRequested() {
        return rerunRequested;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return QUEUED.equals(state) || RUNNING.equals(state);
    }

    /** worker가 작업을 잡는다. 시도 횟수를 여기서 올려 재시도 간격 계산의 근거로 삼는다. */
    public UUID claim(Instant now, Instant leaseUntil) {
        this.state = RUNNING;
        this.attempt = attempt + 1;
        this.leaseToken = UUID.randomUUID();
        this.leaseUntil = leaseUntil;
        this.updatedAt = now;
        return leaseToken;
    }

    /** 수집이 길어지는 동안 임대를 연장한다. 소유자만 연장할 수 있다. */
    public boolean renewLease(UUID token, Instant now, Instant leaseUntil) {
        if (!owns(token)) {
            return false;
        }
        this.leaseUntil = leaseUntil;
        this.updatedAt = now;
        return true;
    }

    public boolean owns(UUID token) {
        return token != null && token.equals(leaseToken) && RUNNING.equals(state);
    }

    public void succeed(Instant now) {
        this.state = SUCCEEDED;
        this.lastErrorCode = null;
        clearLease(now);
    }

    public void fail(String errorCode, Instant now) {
        this.state = FAILED;
        this.lastErrorCode = errorCode;
        clearLease(now);
    }

    /** 재시도한다. 같은 작업을 다시 쓰므로 attempt가 이어지고 backoff가 늘어난다. */
    public void retryAt(Instant dueAt, String errorCode, Instant now) {
        this.state = QUEUED;
        this.lastErrorCode = errorCode;
        this.dueAt = dueAt;
        clearLease(now);
    }

    /** 실행 중에 새 이벤트가 왔다. 작업을 하나 더 만들지 않고 합친 뒤 끝나고 다시 확인한다. */
    public void requestRerun(Instant now) {
        this.rerunRequested = true;
        this.updatedAt = now;
    }

    public void clearRerunRequest(Instant now) {
        this.rerunRequested = false;
        this.updatedAt = now;
    }

    public void rememberTargetRevision(String revision, Instant now) {
        this.targetRevision = revision;
        this.updatedAt = now;
    }

    private void clearLease(Instant now) {
        this.leaseToken = null;
        this.leaseUntil = null;
        this.updatedAt = now;
    }
}
