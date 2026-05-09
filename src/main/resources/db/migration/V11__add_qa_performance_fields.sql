ALTER TABLE qa_message ADD COLUMN retrieval_latency_ms BIGINT NULL;
ALTER TABLE qa_message ADD COLUMN generation_latency_ms BIGINT NULL;
ALTER TABLE qa_message ADD COLUMN retrieval_cache_hit TINYINT NOT NULL DEFAULT 0;
ALTER TABLE qa_message ADD COLUMN answer_mode VARCHAR(32) NULL;