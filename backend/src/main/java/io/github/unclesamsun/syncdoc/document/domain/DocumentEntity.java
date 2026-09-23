package io.github.unclesamsun.syncdoc.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 게시본 안의 문서 하나.
 *
 * <p>id를 저장 전에 정한다. 문서 안의 링크를 같은 게시본의 다른 문서 주소로 바꾸려면 저장하기 전에
 * 서로의 id를 알아야 하기 때문이다.
 *
 * <p>변환에 실패한 문서는 {@code invalid}로 두고 {@code html}을 비운다. 실패한 문서 하나가
 * 게시본 전체를 막지 않게 하되, 그 문서를 정상처럼 보여주지도 않는다.
 */
@Entity
@Table(name = "documents")
public class DocumentEntity {

    /** 수집만 끝나고 아직 변환하지 않은 상태. 변환기가 붙기 전에 만든 게시본에 남아 있다. */
    public static final String COLLECTED = "collected";
    public static final String VALID = "valid";
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

    @Column(name = "links_json", nullable = false)
    private String linksJson;

    @Column(name = "plain_text", nullable = false)
    private String plainText;

    @Column(name = "warnings_json", nullable = false)
    private String warningsJson;

    @Column(nullable = false)
    private String state;

    protected DocumentEntity() {
    }

    public DocumentEntity(UUID id, UUID snapshotId, String path, String title, String sourceHash,
                          String plainText) {
        this.id = id;
        this.snapshotId = snapshotId;
        this.path = path;
        this.title = title;
        this.sourceHash = sourceHash;
        this.plainText = plainText;
        this.headingsJson = "[]";
        this.diagramsJson = "[]";
        this.linksJson = "[]";
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

    public String getHeadingsJson() {
        return headingsJson;
    }

    public String getDiagramsJson() {
        return diagramsJson;
    }

    public String getLinksJson() {
        return linksJson;
    }

    public String getPlainText() {
        return plainText;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public String getState() {
        return state;
    }

    /** 변환 결과를 담는다. JSON 직렬화는 부르는 쪽이 하고 여기서는 문자열만 보관한다. */
    public void rendered(String title, String specId, String kind, String html, String plainText,
                         String headingsJson, String diagramsJson, String linksJson, String warningsJson) {
        this.title = title;
        this.specId = specId;
        this.kind = kind;
        this.html = html;
        this.plainText = plainText;
        this.headingsJson = headingsJson;
        this.diagramsJson = diagramsJson;
        this.linksJson = linksJson;
        this.warningsJson = warningsJson;
        this.state = VALID;
    }

    /** 변환에 실패했다. 본문을 비우고 왜 실패했는지만 남긴다. */
    public void invalid(String warningsJson) {
        this.html = null;
        this.warningsJson = warningsJson;
        this.state = INVALID;
    }
}
