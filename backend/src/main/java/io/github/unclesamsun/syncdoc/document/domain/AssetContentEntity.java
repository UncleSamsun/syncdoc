package io.github.unclesamsun.syncdoc.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 첨부의 내용. 열쇠는 내용 해시라서 같은 바이트는 한 번만 저장된다.
 *
 * <p>내용만으로는 누구 것인지 알 수 없다. 권한 확인은 이 내용을 가리키는 {@link AssetEntity}와
 * 그 게시본으로 한다.
 */
@Entity
@Table(name = "asset_contents")
public class AssetContentEntity {

    @Id
    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(nullable = false, updatable = false)
    private byte[] bytes;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssetContentEntity() {
    }

    public AssetContentEntity(String storageKey, byte[] bytes, Instant createdAt) {
        this.storageKey = storageKey;
        this.bytes = bytes;
        this.byteSize = bytes.length;
        this.createdAt = createdAt;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int getByteSize() {
        return byteSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
