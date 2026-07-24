CREATE TABLE articles_aud (
  id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  tenant_id BIGINT,
  title VARCHAR(255),
  text TEXT,
  status VARCHAR(20),
  failure_reason VARCHAR(500),
  original_file_key VARCHAR(500),
  original_file_name VARCHAR(255),
  original_content_type VARCHAR(100),
  active BOOLEAN,
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);
