CREATE TABLE user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    email VARCHAR(128),
    phone VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    last_login_at TIMESTAMP NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_account_username UNIQUE (username)
);

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

CREATE TABLE user_role_rel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_role_rel UNIQUE (user_id, role_id, deleted)
);

CREATE INDEX idx_user_account_tenant_status ON user_account (tenant_id, status);
CREATE INDEX idx_sys_role_scope_status ON sys_role (scope_type, status);
CREATE INDEX idx_user_role_rel_user ON user_role_rel (user_id);

INSERT INTO sys_role (id, role_code, role_name, scope_type, status, created_by, updated_by, deleted) VALUES
    (1, 'SUPER_ADMIN', 'Platform Super Admin', 'PLATFORM', 'ENABLED', 1, 1, 0),
    (2, 'TENANT_ADMIN', 'Tenant Admin', 'TENANT', 'ENABLED', 1, 1, 0),
    (3, 'KNOWLEDGE_OPERATOR', 'Knowledge Operator', 'TENANT', 'ENABLED', 1, 1, 0),
    (4, 'SUPPORT_AGENT', 'Support Agent', 'TENANT', 'ENABLED', 1, 1, 0),
    (5, 'END_USER', 'End User', 'TENANT', 'ENABLED', 1, 1, 0);

INSERT INTO user_account (
    id,
    tenant_id,
    username,
    password_hash,
    real_name,
    email,
    phone,
    status,
    created_by,
    updated_by,
    deleted
) VALUES
    (
        1,
        0,
        'platform.admin',
        '$2a$10$f1towdmuNnQMqod7yrpz.uS1oyH5ASklOxGPMnbYYNMIk4keoYxWW',
        'Platform Admin',
        'platform.admin@knowflow.local',
        '13000000001',
        'ENABLED',
        1,
        1,
        0
    ),
    (
        2,
        1,
        'tenant.admin',
        '$2a$10$/2.vHnKXxoI1PVet4qhecuNJHbLoViQC5tPE.7Zrhmlt10xqanVKG',
        'Tenant Admin',
        'tenant.admin@knowflow.local',
        '13000000002',
        'ENABLED',
        1,
        1,
        0
    ),
    (
        3,
        1,
        'knowledge.operator',
        '$2a$10$/2.vHnKXxoI1PVet4qhecuNJHbLoViQC5tPE.7Zrhmlt10xqanVKG',
        'Knowledge Operator',
        'knowledge.operator@knowflow.local',
        '13000000003',
        'ENABLED',
        1,
        1,
        0
    );

INSERT INTO user_role_rel (id, user_id, role_id, created_by, updated_by, deleted) VALUES
    (1, 1, 1, 1, 1, 0),
    (2, 2, 2, 1, 1, 0),
    (3, 2, 3, 1, 1, 0),
    (4, 3, 3, 1, 1, 0);
