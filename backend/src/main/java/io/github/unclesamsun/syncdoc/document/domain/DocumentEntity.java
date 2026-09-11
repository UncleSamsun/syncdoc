package io.github.unclesamsun.syncdoc.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 게시본 안의 문서 하나.
 *
 * <p>수집 단계는 원문과 해시까지만 채운다. {@code html}·목차·다이어그램은 문서 변환을 만드는
 * TASK-006이 채우고, 그때 renderer 버전이 올라가 같은 원문도 새 게시본으로 다시 만들어진다.
 */
@Entity
@Table(name = "documents")
public class DocumentEntity {

    public static final String COLLECTED = "collected";
    public static final String INVALID = "invalid";

    @Id
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(nullable = false, updatable = false)
    private String path;

    @Column(name = "spec_id")
    private String specId;

    @Column
    private String kind;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_hash", nullable = false)
    private String sourceHash;

    @Column
    private String html;

    @Column(name = "headings_json", nullable = false)
    private String headingsJson;

    @Column(name = "diagrams_json", nullable = false)
    private String diagramsJson;

    @Column(name = "plain_text", nullable = false)
    private String plainText;

    @Column(name = "warnings_json", nullable = false)
    private String warningsJson;

    @Column(nullable = false)
    private String state;

    protected DocumentEntity() {
    }

    public DocumentEntity(UUID snapshotId, String path, String title, String sourceHash, String plainText) {
        this.id = UUID.randomUUID();
        this.snapshotId = snapshotId;
        this.path = path;
        this.title = title;
        this.sourceHash = sourceHash;
        this.plainText = plainText;
        this.headingsJson = "[]";
        this.diagramsJson = "[]";
        this.warningsJson = "[]";
        this.state = COLLECTED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public String getPath() {
        return path;
    }

    public String getSpecId() {
        return specId;
    }

    public String getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public String getHtml() {
        return html;
    }

    public String getPlainText() {
        return plainText;
    }

    public String getState() {
        return state;
    }
}
