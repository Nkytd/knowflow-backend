CREATE TABLE knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_no INT NOT NULL,
    content TEXT NOT NULL,
    char_count INT NOT NULL DEFAULT 0,
    token_count INT NOT NULL DEFAULT 0,
    source_page INT,
    source_section VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_chunk_doc_no UNIQUE (document_id, chunk_no, deleted)
);

CREATE INDEX idx_chunk_tenant_document ON knowledge_chunk (tenant_id, document_id);
CREATE INDEX idx_chunk_tenant_kb ON knowledge_chunk (tenant_id, knowledge_base_id);
