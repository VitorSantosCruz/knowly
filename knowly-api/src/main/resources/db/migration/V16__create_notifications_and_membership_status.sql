-- tenant-membership-acceptance: adds pending/accept/decline state to
-- TenantMembership (see specify/features/tenant-membership-acceptance/PLAN.md).
-- `status` is additive alongside the existing `active` boolean, which stays
-- the single source of truth every authorization check already reads
-- (PermissionAspect, findByUserAndActiveTrue, etc.) — untouched by this
-- migration or this feature.
ALTER TABLE tenant_memberships ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Explicit backfill (not relying on the column DEFAULT alone for existing
-- rows) so every pre-existing row's status is unambiguously ACTIVE,
-- matching its current `active = true`/`false` reality at migration time:
-- a currently-inactive (removed) row is backfilled to ACTIVE too, since
-- `status` has never meant anything for removed rows until this feature and
-- `active` remains the only column any removal-check reads.
UPDATE tenant_memberships SET status = 'ACTIVE';

ALTER TABLE tenant_memberships_aud ADD COLUMN status VARCHAR(20);
UPDATE tenant_memberships_aud SET status = 'ACTIVE';

CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  recipient_user_id BIGINT NOT NULL REFERENCES users (id),
  type VARCHAR(40) NOT NULL,
  tenant_membership_id BIGINT NOT NULL REFERENCES tenant_memberships (id),
  resolved BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_notifications_recipient_unresolved
  ON notifications (recipient_user_id) WHERE NOT resolved;
