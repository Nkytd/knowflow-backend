CREATE TABLE parse_task_governance_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT,
    task_id BIGINT,
    document_id BIGINT,
    task_no VARCHAR(64),
    task_type VARCHAR(32),
    event_type VARCHAR(64) NOT NULL,
    reason VARCHAR(500),
    worker_id VARCHAR(128),
    attempt_started_at TIMESTAMP NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_task_governance_tenant_time ON parse_task_governance_event (tenant_id, created_at);
CREATE INDEX idx_task_governance_event_type ON parse_task_governance_event (tenant_id, event_type, created_at);
CREATE INDEX idx_task_governance_task ON parse_task_governance_event (task_id, created_at);
