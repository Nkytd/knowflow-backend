CREATE TABLE tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_code UNIQUE (tenant_code)
);

CREATE TABLE knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    kb_code VARCHAR(64) NOT NULL,
    kb_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(32) NOT NULL,
    doc_count INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_kb_tenant_code UNIQUE (tenant_id, kb_code)
);

CREATE TABLE knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    doc_code VARCHAR(64) NOT NULL,
    doc_name VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    storage_type VARCHAR(32) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(64),
    file_size BIGINT NOT NULL DEFAULT 0,
    version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    parse_status VARCHAR(32) NOT NULL,
    index_status VARCHAR(32) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_doc_tenant_code UNIQUE (tenant_id, doc_code)
);

CREATE TABLE parse_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    task_no VARCHAR(64) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    duration_ms BIGINT,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_parse_task_no UNIQUE (task_no)
);

CREATE INDEX idx_kb_tenant_status ON knowledge_base (tenant_id, status);
CREATE INDEX idx_doc_tenant_kb_status ON knowledge_document (tenant_id, knowledge_base_id, status);
CREATE INDEX idx_doc_parse_status ON knowledge_document (tenant_id, parse_status);
CREATE INDEX idx_parse_task_document ON parse_task (tenant_id, document_id);
CREATE INDEX idx_parse_task_status ON parse_task (tenant_id, status);

INSERT INTO tenant (
    id,
    tenant_code,
    tenant_name,
    status,
    contact_name,
    contact_phone,
    created_by,
    updated_by,
    deleted
) VALUES (
    1,
    'demo_tenant',
    'KnowFlow 演示租户',
    'ENABLED',
    '系统初始化',
    '00000000000',
    1,
    1,
    0
);
