-- Document integrity seal storage for Karte document canonical hash and HMAC seal.
CREATE TABLE IF NOT EXISTS d_document_integrity (
    document_id BIGINT NOT NULL,
    seal_version VARCHAR(16) NOT NULL,
    hash_alg VARCHAR(32) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    seal_alg VARCHAR(32) NOT NULL,
    seal CHAR(64) NOT NULL,
    key_id VARCHAR(128) NOT NULL,
    sealed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    sealed_by VARCHAR(128),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT d_document_integrity_pkey PRIMARY KEY (document_id),
    CONSTRAINT fk_d_document_integrity_document FOREIGN KEY (document_id)
        REFERENCES d_document(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS d_document_integrity_sealed_at_idx
    ON d_document_integrity (sealed_at);
