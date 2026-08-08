-- tenant-access-group-bulk-and-delete REQ-8/REQ-9: AccessGroup gains cascading soft-delete, and
-- its owned AccessGroupPermission rows gain the same column so a future revoke path (out of
-- scope here) doesn't have to touch this uniqueness constraint again. Same partial-index pattern
-- as V28's ux_user_access_groups_membership_group.

ALTER TABLE access_groups ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE access_groups_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS access_groups_tenant_id_name_key;
CREATE UNIQUE INDEX ux_access_groups_tenant_name
  ON access_groups (tenant_id, name) WHERE deleted_at IS NULL;

ALTER TABLE access_group_permissions ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE access_group_permissions_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE access_group_permissions DROP CONSTRAINT IF EXISTS access_group_permissions_access_group_id_permission_key;
CREATE UNIQUE INDEX ux_access_group_permissions_group_permission
  ON access_group_permissions (access_group_id, permission) WHERE deleted_at IS NULL;
