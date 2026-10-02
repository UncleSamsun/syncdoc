package io.github.unclesamsun.syncdoc.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 한 revision에서 만든 게시본. 문서를 모두 담은 뒤에야 {@code complete}가 true가 되고,
 * 그 다음에 프로젝트의 현재 snapshot으로 바뀐다. 미완성 snapshot은 어떤 화면에도 쓰이지 않는다.
 *
 * <p>renderer/policy 버전이 식별자에 들어간다. 원문이 같아도 변환 규칙이 바뀌면 다른 게시본이다.
 */
@Entity
@Table(name = "document_snapshots")
public class DocumentSnapshotEntity {

    /** 아직 판정하지 않은 게시본의 기본값. migration의 기본값과 같다. */
    public static final String UNCHECKED = "{\"status\":\"unchecked\",\"uncheckedReason\":"
            + "\"NOT_COMPUTED\",\"truncated\":false,\"findings\":[],\"types\":[]}";

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "source_revision", nullable = false, updatable = false)
    private String sourceRevision;

    @Column(name = "renderer_version", nullable = false, updatable = false)
    private String rendererVersion;

    @Column(name = "policy_version", nullable = false, updatable = false)
    private String policyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean complete;

    /** 산출물 체크리스트(API-025)의 판정 결과. 수집할 때 채운다. */
    @Column(name = "checklist_json", nullable = false)
    private String checklistJson;

    @Column(name = "traceability_json")
    private String traceabilityJson;

    public String getTraceabilityJson() { return traceabilityJson; }
    public void traceability(String json) { this.traceabilityJson = json; }

    @Column(name="collection_branch",updatable=false)
    private String collectionBranch;
    @Column(name="collection_docs_root",updatable=false)
    private String collectionDocsRoot;
    @Column(name="comparison_json")
    private String comparisonJson;
    public String getCollectionBranch(){return collectionBranch;}
    public String getCollectionDocsRoot(){return collectionDocsRoot;}
    public String getComparisonJson(){return comparisonJson;}
    public void comparison(String value){comparisonJson=value;}
    @Column(name = "relations_json")
    private String relationsJson;
    public String getRelationsJson() { return relationsJson; }
    public void relations(String value) { relationsJson = value; }
    @Column(name = "context_json")
    private String contextJson;
    public String getContextJson() { return contextJson; }
    public void context(String value) { contextJson = value; }
    public DocumentSnapshotEntity(UUID projectId,String revision,String renderer,String policy,Instant createdAt,String branch,String docsRoot){
        this(projectId,revision,renderer,policy,createdAt);
        this.collectionBranch=java.util.Objects.requireNonNull(branch);
        this.collectionDocsRoot=java.util.Objects.requireNonNull(docsRoot);
    }

    protected DocumentSnapshotEntity() {
    }

    public DocumentSnapshotEntity(UUID projectId, String sourceRevision, String rendererVersion,
                                  String policyVersion, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.sourceRevision = sourceRevision;
        this.rendererVersion = rendererVersion;
        this.policyVersion = policyVersion;
        this.createdAt = createdAt;
        this.complete = false;
        // 아직 판정하지 않았다. 판정하지 않은 것을 통과로 보이게 하지 않는다.
        this.checklistJson = UNCHECKED;
    }

    public UUID getId() {
        return id;
    }

    public String getChecklistJson() {
        return checklistJson;
    }

    /** 수집이 판정을 마친 뒤에 부른다. */
    public void checklist(String json) {
        this.checklistJson = json;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getSourceRevision() {
        return sourceRevision;
    }

    public String getRendererVersion() {
        return rendererVersion;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isComplete() {
        return complete;
    }

    /** 문서를 모두 저장한 뒤에만 부른다. 부분 수집을 완료로 바꾸지 않는다. */
    public void markComplete() {
        this.complete = true;
    }
}
