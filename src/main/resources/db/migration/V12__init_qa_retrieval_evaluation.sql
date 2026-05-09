CREATE TABLE qa_retrieval_eval_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    case_name VARCHAR(128) NOT NULL,
    question_text TEXT NOT NULL,
    expected_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    expected_document_id BIGINT NULL,
    expected_document_name VARCHAR(255) NULL,
    expected_keywords VARCHAR(1000) NULL,
    top_k INT NOT NULL DEFAULT 5,
    enabled TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500) NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE qa_retrieval_eval_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    run_no VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NULL,
    total_cases INT NOT NULL DEFAULT 0,
    passed_cases INT NOT NULL DEFAULT 0,
    failed_cases INT NOT NULL DEFAULT 0,
    pass_rate DECIMAL(10,4) NOT NULL DEFAULT 0,
    recall_at_k DECIMAL(10,4) NOT NULL DEFAULT 0,
    top1_hit_rate DECIMAL(10,4) NOT NULL DEFAULT 0,
    no_hit_accuracy DECIMAL(10,4) NOT NULL DEFAULT 0,
    avg_top_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    avg_top_lexical_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    avg_top_vector_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_qa_retrieval_eval_run_no UNIQUE (run_no)
);

CREATE TABLE qa_retrieval_eval_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    expected_status VARCHAR(32) NOT NULL,
    actual_status VARCHAR(32) NOT NULL,
    expected_document_id BIGINT NULL,
    expected_document_name VARCHAR(255) NULL,
    actual_top_document_id BIGINT NULL,
    actual_top_document_name VARCHAR(255) NULL,
    top_recall_score DECIMAL(10,4) NULL,
    top_lexical_score DECIMAL(10,4) NULL,
    top_vector_score DECIMAL(10,4) NULL,
    hit_rank INT NULL,
    keyword_hit_count INT NOT NULL DEFAULT 0,
    keyword_total_count INT NOT NULL DEFAULT 0,
    passed TINYINT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(500) NULL,
    hits_json LONGTEXT NULL,
    query_variant_json LONGTEXT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_qa_retrieval_eval_case_kb ON qa_retrieval_eval_case (tenant_id, knowledge_base_id, enabled);
CREATE INDEX idx_qa_retrieval_eval_run_tenant ON qa_retrieval_eval_run (tenant_id, created_at);
CREATE INDEX idx_qa_retrieval_eval_result_run ON qa_retrieval_eval_result (tenant_id, run_id, passed);
