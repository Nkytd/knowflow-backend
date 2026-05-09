CREATE TABLE qa_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    session_no VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_message_at TIMESTAMP NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_qa_session_no UNIQUE (session_no)
);

CREATE TABLE qa_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    answer_text LONGTEXT,
    answer_status VARCHAR(32) NOT NULL,
    model_name VARCHAR(64),
    prompt_version VARCHAR(32),
    latency_ms BIGINT,
    input_tokens INT,
    output_tokens INT,
    source_count INT NOT NULL DEFAULT 0,
    need_human_handoff TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE retrieval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    qa_message_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    recall_score DECIMAL(10,4) NOT NULL,
    rank_no INT NOT NULL,
    snippet_text VARCHAR(1000) NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE feedback_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    qa_message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    feedback_type VARCHAR(16) NOT NULL,
    feedback_reason VARCHAR(255),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_feedback_message_user UNIQUE (qa_message_id, user_id, deleted)
);

CREATE INDEX idx_qa_session_user ON qa_session (tenant_id, user_id, last_message_at);
CREATE INDEX idx_qa_message_session ON qa_message (tenant_id, session_id, created_at);
CREATE INDEX idx_retrieval_message_rank ON retrieval_record (qa_message_id, rank_no);
CREATE INDEX idx_feedback_message_user ON feedback_record (qa_message_id, user_id);
