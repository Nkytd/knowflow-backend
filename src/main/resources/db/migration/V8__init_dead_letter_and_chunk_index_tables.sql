CREATE TABLE knowledge_chunk_index (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    embedding_provider VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    embedding_dim INT NOT NULL,
    vector_norm DECIMAL(18,8) NOT NULL DEFAULT 0,
    embedding_json LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_chunk_index_chunk UNIQUE (chunk_id)
);

CREATE TABLE dead_letter_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT,
    dead_letter_no VARCHAR(64) NOT NULL,
    task_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    task_no VARCHAR(64),
    document_id BIGINT,
    source_queue VARCHAR(128) NOT NULL,
    source_exchange VARCHAR(128),
    routing_key VARCHAR(128),
    dead_letter_reason VARCHAR(64) NOT NULL,
    error_message VARCHAR(1000),
    payload_json LONGTEXT,
    retry_attempt INT NOT NULL DEFAULT 1,
    replay_status VARCHAR(32) NOT NULL,
    next_retry_at TIMESTAMP NULL,
    replayed_at TIMESTAMP NULL,
    resolved_at TIMESTAMP NULL,
    replay_mode VARCHAR(32),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_dead_letter_no UNIQUE (dead_letter_no)
);

ALTER TABLE retrieval_record ADD COLUMN lexical_score DECIMAL(10,4) NULL;
ALTER TABLE retrieval_record ADD COLUMN vector_score DECIMAL(10,4) NULL;
ALTER TABLE retrieval_record ADD COLUMN recall_strategy VARCHAR(32) NULL;

CREATE INDEX idx_chunk_index_kb_status ON knowledge_chunk_index (tenant_id, knowledge_base_id, status);
CREATE INDEX idx_chunk_index_document ON knowledge_chunk_index (tenant_id, document_id);
CREATE INDEX idx_dead_letter_task ON dead_letter_message (task_id, created_at);
CREATE INDEX idx_dead_letter_status_retry ON dead_letter_message (replay_status, next_retry_at);
CREATE INDEX idx_dead_letter_tenant_type ON dead_letter_message (tenant_id, task_type, created_at);
