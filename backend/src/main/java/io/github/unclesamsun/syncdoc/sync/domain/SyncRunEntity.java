package io.github.unclesamsun.syncdoc.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 수집 시도 하나의 기록. 마지막 시도와 마지막 성공을 구분하려고 작업과 분리해 남긴다.
 *
 * <p>진단에는 오류 코드와 저장소 안의 경로만 담는다. 토큰·서버 내부 경로는 넣지 않는다.
 */
@Entity
@Table(name = "sync_runs")
public class SyncRunEntity {

    public static final String RUNNING = "running";
    public static final String SUCCEEDED = "succeeded";
    public static final String FAILED = "failed";

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "source_revision")
    private String sourceRevision;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "diagnostics_json", nullable = false)
    private String diagnosticsJson;

    protected SyncRunEntity() {
    }

    public SyncRunEntity(UUID projectId, UUID jobId, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.jobId = jobId;
        this.startedAt = startedAt;
        this.outcome = RUNNING;
        this.diagnosticsJson = "{}";
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getSourceRevision() {
        return sourceRevision;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDiagnosticsJson() {
        return diagnosticsJson;
    }

    public void succeeded(String sourceRevision, Instant finishedAt, String diagnosticsJson) {
        this.outcome = SUCCEEDED;
        this.sourceRevision = sourceRevision;
        this.finishedAt = finishedAt;
        this.diagnosticsJson = diagnosticsJson;
    }

    public void failed(String errorCode, Instant finishedAt, String diagnosticsJson) {
        this.outcome = FAILED;
        this.errorCode = errorCode;
        this.finishedAt = finishedAt;
        this.diagnosticsJson = diagnosticsJson;
    }

    public void observedRevision(String sourceRevision) {
        this.sourceRevision = sourceRevision;
    }
}
