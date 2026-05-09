CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    biz_type VARCHAR(64) NOT NULL,
    biz_id BIGINT NULL,
    biz_no VARCHAR(255) NULL,
    operator_user_id BIGINT NULL,
    operator_username VARCHAR(64) NULL,
    operator_real_name VARCHAR(64) NULL,
    request_method VARCHAR(16) NULL,
    request_uri VARCHAR(512) NULL,
    operation_summary VARCHAR(500) NOT NULL,
    success_flag TINYINT NOT NULL DEFAULT 1,
    error_message VARCHAR(1000) NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_log_tenant_created ON audit_log (tenant_id, created_at);
CREATE INDEX idx_audit_log_tenant_module_created ON audit_log (tenant_id, module_code, created_at);
CREATE INDEX idx_audit_log_tenant_biz_created ON audit_log (tenant_id, biz_type, biz_id, created_at);
CREATE INDEX idx_audit_log_tenant_operator_created ON audit_log (tenant_id, operator_user_id, created_at);
