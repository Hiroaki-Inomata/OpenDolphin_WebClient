package open.dolphin.security.integrity;

import java.io.Serializable;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Stores document integrity metadata (canonical hash and HMAC seal).
 */
@Entity
@Table(name = "d_document_integrity")
public class DocumentIntegrityEntity implements Serializable {

    @Id
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "seal_version", nullable = false, length = 16)
    private String sealVersion;

    @Column(name = "hash_alg", nullable = false, length = 32)
    private String hashAlg;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "seal_alg", nullable = false, length = 32)
    private String sealAlg;

    @Column(name = "seal", nullable = false, length = 64)
    private String seal;

    @Column(name = "key_id", nullable = false, length = 128)
    private String keyId;

    @Column(name = "sealed_at", nullable = false)
    private Instant sealedAt;

    @Column(name = "sealed_by", length = 128)
    private String sealedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (sealedAt == null) {
            sealedAt = now;
        }
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getSealVersion() {
        return sealVersion;
    }

    public void setSealVersion(String sealVersion) {
        this.sealVersion = sealVersion;
    }

    public String getHashAlg() {
        return hashAlg;
    }

    public void setHashAlg(String hashAlg) {
        this.hashAlg = hashAlg;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSealAlg() {
        return sealAlg;
    }

    public void setSealAlg(String sealAlg) {
        this.sealAlg = sealAlg;
    }

    public String getSeal() {
        return seal;
    }

    public void setSeal(String seal) {
        this.seal = seal;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public Instant getSealedAt() {
        return sealedAt;
    }

    public void setSealedAt(Instant sealedAt) {
        this.sealedAt = sealedAt;
    }

    public String getSealedBy() {
        return sealedBy;
    }

    public void setSealedBy(String sealedBy) {
        this.sealedBy = sealedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
