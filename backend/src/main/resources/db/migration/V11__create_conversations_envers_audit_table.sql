CREATE TABLE conversations_aud (
  id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  tenant_id BIGINT,
  owner_user_id BIGINT,
  title VARCHAR(255),
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);
