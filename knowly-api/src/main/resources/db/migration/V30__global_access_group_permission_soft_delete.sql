-- role-permission-revoke REQ-3/REQ-10: global_access_group_permissions gains the same deleted_at
-- soft-delete column access_group_permissions already has (V29), so staff/global-scope permission
-- revoke can be implemented as a soft-delete on this table too. Same partial-index substitution
-- V29 already did for the tenant-side table's equivalent constraint.

ALTER TABLE global_access_group_permissions ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE global_access_group_permissions_aud ADD COLUMN deleted_at TIMESTAMPTZ;

-- The table-level UNIQUE (global_access_group_id, permission) constraint added by V14 was never
-- explicitly named, so Postgres auto-generated (and silently truncated) its name -- look it up by
-- introspecting pg_constraint rather than guessing the truncated identifier.
DO $$
DECLARE
  constraint_name TEXT;
BEGIN
  SELECT conname INTO constraint_name
  FROM pg_constraint
  WHERE conrelid = 'global_access_group_permissions'::regclass
    AND contype = 'u';

  IF constraint_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE global_access_group_permissions DROP CONSTRAINT %I', constraint_name);
  END IF;
END $$;

CREATE UNIQUE INDEX ux_global_access_group_permissions_group_permission
  ON global_access_group_permissions (global_access_group_id, permission) WHERE deleted_at IS NULL;
