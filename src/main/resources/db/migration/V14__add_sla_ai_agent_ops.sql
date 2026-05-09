ALTER TABLE ticket ADD COLUMN sla_policy VARCHAR(64) NULL;
ALTER TABLE ticket ADD COLUMN sla_due_at TIMESTAMP NULL;
ALTER TABLE ticket ADD COLUMN sla_status VARCHAR(32) NOT NULL DEFAULT 'ON_TRACK';
ALTER TABLE ticket ADD COLUMN sla_breached_at TIMESTAMP NULL;
ALTER TABLE ticket ADD COLUMN sla_reminder_sent_at TIMESTAMP NULL;

CREATE INDEX idx_ticket_tenant_sla ON ticket (tenant_id, sla_status, sla_due_at);
CREATE INDEX idx_ticket_tenant_priority_status ON ticket (tenant_id, priority, status);
