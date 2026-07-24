CREATE TABLE articles (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants (id),
  title VARCHAR(255) NOT NULL,
  text TEXT,
  status VARCHAR(20) NOT NULL,
  failure_reason VARCHAR(500),
  original_file_key VARCHAR(500) NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  original_content_type VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE INDEX ix_articles_tenant ON articles (tenant_id);
