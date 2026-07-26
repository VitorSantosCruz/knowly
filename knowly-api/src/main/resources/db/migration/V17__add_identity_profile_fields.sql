-- identity-profile-model: personal-data fields for User, company-record
-- fields for Tenant, a nullable-FK relaxation on notifications so a
-- profile-edit-request notification can anchor to something other than a
-- TenantMembership, and the new profile_edit_requests table.
-- See specify/features/identity-profile-model/PLAN.md ("Data schema").

-- User personal-data fields (REQ-1). Nullable, no backfill, mirroring the
-- onboarding_completed_at precedent (V6).
ALTER TABLE users ADD COLUMN full_name VARCHAR(255);
ALTER TABLE users ADD COLUMN address VARCHAR(500);
ALTER TABLE users ADD COLUMN rg VARCHAR(255);           -- encrypted (Base64 ciphertext)
ALTER TABLE users ADD COLUMN cpf VARCHAR(255);          -- encrypted (Base64 ciphertext)
ALTER TABLE users ADD COLUMN phone VARCHAR(50);
ALTER TABLE users ADD COLUMN rg_blind_index VARCHAR(64);
ALTER TABLE users ADD COLUMN cpf_blind_index VARCHAR(64);

-- REQ-2/REQ-2a: DB-level global uniqueness, partial (existing rows with the
-- field unset are unaffected). Uniqueness for cpf/rg lives on the blind-index
-- columns only (SPEC's "Resolved" section) -- the encrypted columns
-- themselves are never used for equality.
CREATE UNIQUE INDEX ux_users_address ON users (address) WHERE address IS NOT NULL;
CREATE UNIQUE INDEX ux_users_phone ON users (phone) WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX ux_users_rg_blind_index ON users (rg_blind_index) WHERE rg_blind_index IS NOT NULL;
CREATE UNIQUE INDEX ux_users_cpf_blind_index ON users (cpf_blind_index) WHERE cpf_blind_index IS NOT NULL;

ALTER TABLE users_aud ADD COLUMN full_name VARCHAR(255);
ALTER TABLE users_aud ADD COLUMN address VARCHAR(500);
ALTER TABLE users_aud ADD COLUMN rg VARCHAR(255);
ALTER TABLE users_aud ADD COLUMN cpf VARCHAR(255);
ALTER TABLE users_aud ADD COLUMN phone VARCHAR(50);
ALTER TABLE users_aud ADD COLUMN rg_blind_index VARCHAR(64);
ALTER TABLE users_aud ADD COLUMN cpf_blind_index VARCHAR(64);

-- Tenant company-record fields (REQ-6/7/7a/7b). Nullable at the DB level
-- (retrofit), required at the service layer for new/edited tenants going
-- forward -- see PLAN.md's "New Tenant fields" note.
ALTER TABLE tenants ADD COLUMN cnpj VARCHAR(20);
ALTER TABLE tenants ADD COLUMN razao_social VARCHAR(255);
ALTER TABLE tenants ADD COLUMN nome_fantasia VARCHAR(255);
ALTER TABLE tenants ADD COLUMN inscricao_estadual VARCHAR(30);

CREATE UNIQUE INDEX ux_tenants_cnpj ON tenants (cnpj) WHERE cnpj IS NOT NULL;
CREATE UNIQUE INDEX ux_tenants_inscricao_estadual ON tenants (inscricao_estadual) WHERE inscricao_estadual IS NOT NULL;

ALTER TABLE tenants_aud ADD COLUMN cnpj VARCHAR(20);
ALTER TABLE tenants_aud ADD COLUMN razao_social VARCHAR(255);
ALTER TABLE tenants_aud ADD COLUMN nome_fantasia VARCHAR(255);
ALTER TABLE tenants_aud ADD COLUMN inscricao_estadual VARCHAR(30);

-- New profile_edit_requests table (REQ-15..21). Not Envers-audited -- see
-- PLAN.md ("ephemeral request state; the resulting User field change is
-- itself Envers-audited via users_aud").
CREATE TABLE profile_edit_requests (
  id BIGSERIAL PRIMARY KEY,
  requester_user_id BIGINT NOT NULL REFERENCES users (id),
  proposed_full_name VARCHAR(255),
  proposed_address VARCHAR(500),
  proposed_rg VARCHAR(255),          -- encrypted, same converter as User.rg
  proposed_cpf VARCHAR(255),         -- encrypted, same converter as User.cpf
  proposed_phone VARCHAR(50),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  resolved_by_user_id BIGINT REFERENCES users (id),
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_profile_edit_requests_requester_pending
  ON profile_edit_requests (requester_user_id) WHERE status = 'PENDING';

-- Relax notifications.tenant_membership_id to nullable and add a nullable
-- profile_edit_request_id FK (REQ-16) -- exactly one of the two is ever set,
-- enforced by a CHECK constraint. Every existing row still has
-- tenant_membership_id populated; every existing query path
-- (NotificationService.listMine, the recipient-scoped accept/decline
-- lookups) filters by recipient/id first and only reads tenantMembership on
-- a membership-invitation-only code path -- see PLAN.md's "New
-- ProfileEditRequest entity" section for the full analysis.
ALTER TABLE notifications ALTER COLUMN tenant_membership_id DROP NOT NULL;
ALTER TABLE notifications ADD COLUMN profile_edit_request_id BIGINT
  REFERENCES profile_edit_requests (id);
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_exactly_one_ref
  CHECK (
    (tenant_membership_id IS NOT NULL AND profile_edit_request_id IS NULL)
    OR (tenant_membership_id IS NULL AND profile_edit_request_id IS NOT NULL)
  );
