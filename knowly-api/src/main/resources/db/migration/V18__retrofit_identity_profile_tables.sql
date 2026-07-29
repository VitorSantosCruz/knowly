-- identity-profile-model-v2: split users' flat personal-data columns into
-- user_profiles (1:1)/addresses (1:1)/contacts (1:n), a narrower
-- LGPD-minimized field set, and a self-approval CHECK on
-- profile_edit_requests. See
-- specify/features/identity-profile-model-v2/PLAN.md ("Data schema").

-- cancel any in-flight PENDING request before its shape changes further
-- (open decision b) -- issued before the ALTER/DROP below so it runs
-- against the still-old shape.
UPDATE profile_edit_requests SET status = 'CANCELLED', resolved_at = now()
  WHERE status = 'PENDING';

CREATE TABLE user_profiles (
  user_id             BIGINT PRIMARY KEY REFERENCES users(id),
  full_name           VARCHAR(255),
  cpf                 VARCHAR(255),
  cpf_blind_index     VARCHAR(64),
  rg                  VARCHAR(255),
  rg_orgao_emissor    VARCHAR(20),
  rg_blind_index      VARCHAR(64),
  birth_date          DATE,
  avatar_url          VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  CHECK ((cpf IS NULL) = (cpf_blind_index IS NULL)),
  CHECK ((rg IS NULL) = (rg_blind_index IS NULL))
);
CREATE UNIQUE INDEX ux_user_profiles_cpf_blind_index ON user_profiles (cpf_blind_index) WHERE cpf_blind_index IS NOT NULL;
CREATE UNIQUE INDEX ux_user_profiles_rg_blind_index ON user_profiles (rg_blind_index) WHERE rg_blind_index IS NOT NULL;

CREATE TABLE addresses (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  cep VARCHAR(9) NOT NULL CHECK (cep ~ '^\d{5}-?\d{3}$'),
  logradouro VARCHAR(255) NOT NULL,
  numero VARCHAR(20),
  complemento VARCHAR(100),
  bairro VARCHAR(100) NOT NULL,
  cidade VARCHAR(100) NOT NULL,
  estado VARCHAR(2) NOT NULL CHECK (estado ~ '^[A-Z]{2}$'),
  pais VARCHAR(100) NOT NULL DEFAULT 'Brasil',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE TABLE contacts (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  type VARCHAR(20) NOT NULL,
  value VARCHAR(255) NOT NULL,
  label VARCHAR(50),
  is_primary BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);
CREATE INDEX idx_contacts_user_id ON contacts (user_id);
CREATE UNIQUE INDEX ux_contacts_primary_per_type ON contacts (user_id, type) WHERE is_primary;

-- retrofit profile_edit_requests: replace V17's flat proposed_* (still
-- present) with the same shape re-pointed at the new tables, plus new
-- proposed_birth_date/proposed_rg_orgao_emissor, plus status gains
-- CANCELLED, plus the self-approval CHECK.
ALTER TABLE profile_edit_requests ADD COLUMN proposed_rg_orgao_emissor VARCHAR(20);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_birth_date DATE;
ALTER TABLE profile_edit_requests ADD COLUMN proposed_cep VARCHAR(9);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_logradouro VARCHAR(255);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_numero VARCHAR(20);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_complemento VARCHAR(100);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_bairro VARCHAR(100);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_cidade VARCHAR(100);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_estado VARCHAR(2);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_pais VARCHAR(100);
-- old proposed_address (free-text) column dropped, unused going forward
ALTER TABLE profile_edit_requests DROP COLUMN proposed_address;
ALTER TABLE profile_edit_requests ADD CONSTRAINT chk_profile_edit_requests_no_self_approval
  CHECK (resolved_by_user_id IS NULL OR resolved_by_user_id <> requester_user_id);
-- status VARCHAR(20) already has no DB-level enum constraint (Java enum only) --
-- CANCELLED is just a new value the Java ProfileEditRequestStatus enum accepts.

CREATE TABLE profile_edit_request_contacts (
  id                       BIGSERIAL PRIMARY KEY,
  profile_edit_request_id BIGINT NOT NULL REFERENCES profile_edit_requests(id),
  action                   VARCHAR(10) NOT NULL,
  -- ON DELETE SET NULL: this row is historical request state, not a live reference -- once the
  -- request is resolved and (for a REMOVE action) the underlying Contact row is actually deleted,
  -- this row must not block that delete via FK.
  contact_id               BIGINT REFERENCES contacts(id) ON DELETE SET NULL,
  type                     VARCHAR(20),
  value                    VARCHAR(255),
  label                    VARCHAR(50),
  is_primary               BOOLEAN,
  -- Only ADD's "no contact_id yet" shape is enforced at the DB level; UPDATE/REMOVE's
  -- "contact_id set at submission time" is a service-layer guarantee only, since ON DELETE SET
  -- NULL above must be able to null this column out later without violating the CHECK.
  CHECK (action != 'ADD' OR contact_id IS NULL)
);
CREATE INDEX idx_profile_edit_request_contacts_request ON profile_edit_request_contacts (profile_edit_request_id);

-- backfill from users into the new tables (REQ-24)
INSERT INTO user_profiles (user_id, full_name, cpf, cpf_blind_index, rg, rg_blind_index, created_by, updated_by)
  SELECT id, full_name, cpf, cpf_blind_index, rg, rg_blind_index, 'migration', 'migration'
  FROM users WHERE full_name IS NOT NULL OR cpf IS NOT NULL OR rg IS NOT NULL;
-- users with none of the above still get an eager empty row (REQ-1):
INSERT INTO user_profiles (user_id, created_by, updated_by)
  SELECT id, 'migration', 'migration' FROM users
  WHERE id NOT IN (SELECT user_id FROM user_profiles);

INSERT INTO contacts (user_id, type, value, is_primary, created_by, updated_by)
  SELECT id, 'PHONE', phone, true, 'migration', 'migration'
  FROM users WHERE phone IS NOT NULL;
-- users.address is explicitly NOT migrated (REQ-26).

-- Envers audit tables, mirroring users_aud's shape (V2).
CREATE TABLE user_profiles_aud (
  user_id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  full_name VARCHAR(255),
  cpf VARCHAR(255),
  cpf_blind_index VARCHAR(64),
  rg VARCHAR(255),
  rg_orgao_emissor VARCHAR(20),
  rg_blind_index VARCHAR(64),
  birth_date DATE,
  avatar_url VARCHAR(500),
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (user_id, rev)
);

CREATE TABLE addresses_aud (
  user_id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  cep VARCHAR(9),
  logradouro VARCHAR(255),
  numero VARCHAR(20),
  complemento VARCHAR(100),
  bairro VARCHAR(100),
  cidade VARCHAR(100),
  estado VARCHAR(2),
  pais VARCHAR(100),
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (user_id, rev)
);

CREATE TABLE contacts_aud (
  id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  user_id BIGINT,
  type VARCHAR(20),
  value VARCHAR(255),
  label VARCHAR(50),
  is_primary BOOLEAN,
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);
