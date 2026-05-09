CREATE TABLE knowledge_draft (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    source_ticket_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    draft_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    question_text VARCHAR(1000) NOT NULL,
    answer_text LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reviewer_user_id BIGINT NULL,
    review_remark VARCHAR(1000) NULL,
    published_document_id BIGINT NULL,
    published_at TIMESTAMP NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_knowledge_draft_source_ticket UNIQUE (source_ticket_id, deleted)
);

CREATE INDEX idx_knowledge_draft_tenant_status ON knowledge_draft (tenant_id, status, updated_at);
CREATE INDEX idx_knowledge_draft_kb_status ON knowledge_draft (tenant_id, knowledge_base_id, status);
CREATE INDEX idx_knowledge_draft_published_document ON knowledge_draft (published_document_id);
