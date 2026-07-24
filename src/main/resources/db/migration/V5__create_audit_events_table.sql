CREATE TABLE audit_events (
  id BIGSERIAL PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_user_id BIGINT REFERENCES users (id),
  tenant_id BIGINT REFERENCES tenants (id),
  action VARCHAR(150) NOT NULL,
  resource_type VARCHAR(100),
  resource_id VARCHAR(100),
  outcome VARCHAR(20) NOT NULL,
  metadata JSONB
);

CREATE INDEX ix_audit_events_tenant_time ON audit_events (tenant_id, occurred_at);
CREATE INDEX ix_audit_events_actor_time ON audit_events (actor_user_id, occurred_at);
