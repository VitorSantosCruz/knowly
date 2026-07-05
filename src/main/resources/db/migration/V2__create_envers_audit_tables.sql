CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revinfo (
  rev BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('revinfo_seq'),
  revtstmp BIGINT
);

CREATE TABLE users_aud (
  id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  email VARCHAR(255),
  one_time_password_hash VARCHAR(255),
  one_time_password_issued_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);
