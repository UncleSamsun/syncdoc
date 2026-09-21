package io.github.unclesamsun.syncdoc.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 게시본 안의 첨부 하나. 문서와 같은 게시본에 묶여 있어야 과거 게시본의 그림을 현재 권한으로
 * 되돌려 주는 일이 생기지 않는다.
 *
 * <p>내용은 {@link AssetContentEntity}가 해시를 열쇠로 보관한다. 같은 그림이 여러 게시본에
 * 나와도 바이트는 한 벌만 남는다.
 */
@Entity
@Table(name = "assets")
public class AssetEntity {

    @Id
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(nullable = false, updatable = false)
    private String path;

    @Column(nullable = false)
    private String mime;

    @Column(name = "bytes_hash", nullable = false)
    private String bytesHash;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    protected AssetEntity() {
    }

    public AssetEntity(UUID id, UUID snapshotId, String path, String mime, String bytesHash, int byteSize) {
        this.id = id;
        this.snapshotId = snapshotId;
        this.path = path;
        this.mime = mime;
        this.bytesHash = bytesHash;
        this.storageKey = bytesHash;
        this.byteSize = byteSize;
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

    public String getMime() {
        return mime;
    }

    public String getBytesHash() {
        return bytesHash;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public int getByteSize() {
        return byteSize;
    }
}
