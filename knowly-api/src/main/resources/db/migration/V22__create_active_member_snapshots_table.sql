CREATE TABLE active_member_snapshots (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants (id),
  snapshot_date DATE NOT NULL,
  active_count BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (tenant_id, snapshot_date)
);

CREATE INDEX ix_active_member_snapshots_tenant_date
  ON active_member_snapshots (tenant_id, snapshot_date);
