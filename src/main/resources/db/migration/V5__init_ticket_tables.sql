CREATE TABLE ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    ticket_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_qa_message_id BIGINT NULL,
    reporter_user_id BIGINT NOT NULL,
    assignee_user_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    last_reply_at TIMESTAMP NULL,
    resolved_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ticket_no UNIQUE (ticket_no)
);

CREATE TABLE ticket_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    operator_user_id BIGINT NOT NULL,
    remark VARCHAR(1000),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE ticket_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    comment_type VARCHAR(32) NOT NULL,
    comment_user_id BIGINT NOT NULL,
    content LONGTEXT NOT NULL,
    visible_to_user TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ticket_tenant_status ON ticket (tenant_id, status, updated_at);
CREATE INDEX idx_ticket_tenant_assignee ON ticket (tenant_id, assignee_user_id, status);
CREATE INDEX idx_ticket_tenant_reporter ON ticket (tenant_id, reporter_user_id, updated_at);
CREATE INDEX idx_ticket_tenant_source_qa ON ticket (tenant_id, source_qa_message_id);
CREATE INDEX idx_ticket_flow_ticket_created ON ticket_flow (ticket_id, created_at);
CREATE INDEX idx_ticket_comment_ticket_created ON ticket_comment (ticket_id, created_at);
