-- logical-delete-everywhere: every destructive operation in the system must be a soft delete,
-- never a physical row removal (standing product decision, 2026-08-04). Retrofits deleted_at
-- onto every entity that was previously hard-deleted (users, contacts, direct permission/access
-- grants) or that needs a genuine "gone" marker distinct from its existing active/status flag
-- (tenant_memberships' hard-delete path), plus the tightly-owned 1:1 profile/address rows so a
-- deleted user's profile data is marked gone alongside it, and articles/conversations so a
-- deleted tenant's own resources are marked gone alongside it. Same pattern as V25's
-- tenants.deleted_at (partial unique indexes so a soft-deleted row's unique keys can be reused).

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE users_aud ADD COLUMN deleted_at TIMESTAMPTZ;
DROP INDEX IF EXISTS ux_users_email_lower;
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email)) WHERE deleted_at IS NULL;

ALTER TABLE contacts ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE contacts_aud ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE user_profiles ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE user_profiles_aud ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE addresses ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE addresses_aud ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE direct_global_permission_grants ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE direct_global_permission_grants_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE direct_global_permission_grants DROP CONSTRAINT IF EXISTS direct_global_permission_grants_user_id_permission_key;
CREATE UNIQUE INDEX ux_direct_global_permission_grants_user_permission
  ON direct_global_permission_grants (user_id, permission) WHERE deleted_at IS NULL;

ALTER TABLE user_global_access_groups ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE user_global_access_groups_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE user_global_access_groups DROP CONSTRAINT IF EXISTS user_global_access_groups_user_id_global_access_group_id_key;
CREATE UNIQUE INDEX ux_user_global_access_groups_user_group
  ON user_global_access_groups (user_id, global_access_group_id) WHERE deleted_at IS NULL;

ALTER TABLE direct_permission_grants ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE direct_permission_grants_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE direct_permission_grants DROP CONSTRAINT IF EXISTS direct_permission_grants_tenant_membership_id_permission_key;
CREATE UNIQUE INDEX ux_direct_permission_grants_membership_permission
  ON direct_permission_grants (tenant_membership_id, permission) WHERE deleted_at IS NULL;

ALTER TABLE user_access_groups ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE user_access_groups_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE user_access_groups DROP CONSTRAINT IF EXISTS user_access_groups_tenant_membership_id_access_group_id_key;
CREATE UNIQUE INDEX ux_user_access_groups_membership_group
  ON user_access_groups (tenant_membership_id, access_group_id) WHERE deleted_at IS NULL;

-- Distinct from the existing `active` boolean (which already means "removed from the tenant,
-- but the membership row and its history stay"): `deleted_at` marks the stronger "hard delete"
-- action REQ-7/8/10/11 already specify, now implemented as a logical delete instead of a
-- physical one.
ALTER TABLE tenant_memberships ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE tenant_memberships_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE tenant_memberships DROP CONSTRAINT IF EXISTS tenant_memberships_user_id_tenant_id_key;
CREATE UNIQUE INDEX ux_tenant_memberships_user_tenant
  ON tenant_memberships (user_id, tenant_id) WHERE deleted_at IS NULL;

-- Resource cascade: a deleted tenant's own articles/conversations no longer make sense to keep
-- live (2026-08-04 product decision) -- Article already has an `active` flag reused as its
-- deleted marker; Conversation gets a genuine `deleted_at` to match.
ALTER TABLE conversations ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE conversations_aud ADD COLUMN deleted_at TIMESTAMPTZ;
