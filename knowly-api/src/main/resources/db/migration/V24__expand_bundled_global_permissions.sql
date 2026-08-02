-- permission-granularity-model REQ-9/REQ-10/REQ-11: replace the three bundled GlobalPermissions
-- (TENANT_MEMBER_MANAGE_ANY, TENANT_ACCESS_GROUP_MANAGE_ANY, TENANT_PERMISSION_GRANT_MANAGE_ANY)
-- with their granular successors, preserving every existing holder's effective access (migration
-- safety requirement in PLAN.md/SPEC.md's non-functional requirements). Additive-then-subtractive:
-- insert every granular replacement for every current holder (direct grants and global
-- access-group grants alike), using ON CONFLICT DO NOTHING against each table's existing unique
-- constraint so an improbable pre-existing holder of a granular permission isn't duplicated, then
-- delete the bundled rows once every replacement is in place.

-- TENANT_MEMBER_MANAGE_ANY -> TENANT_MEMBER_VIEW, TENANT_MEMBER_CREATE, TENANT_MEMBER_EDIT, TENANT_MEMBER_DELETE
INSERT INTO direct_global_permission_grants (user_id, permission, created_at, created_by, updated_at, updated_by)
SELECT user_id, v.permission, created_at, created_by, updated_at, updated_by
FROM direct_global_permission_grants, LATERAL (
  VALUES ('TENANT_MEMBER_VIEW'), ('TENANT_MEMBER_CREATE'), ('TENANT_MEMBER_EDIT'), ('TENANT_MEMBER_DELETE')
) AS v(permission)
WHERE direct_global_permission_grants.permission = 'TENANT_MEMBER_MANAGE_ANY'
ON CONFLICT (user_id, permission) DO NOTHING;

INSERT INTO global_access_group_permissions (global_access_group_id, permission, created_at, created_by, updated_at, updated_by)
SELECT global_access_group_id, v.permission, created_at, created_by, updated_at, updated_by
FROM global_access_group_permissions, LATERAL (
  VALUES ('TENANT_MEMBER_VIEW'), ('TENANT_MEMBER_CREATE'), ('TENANT_MEMBER_EDIT'), ('TENANT_MEMBER_DELETE')
) AS v(permission)
WHERE global_access_group_permissions.permission = 'TENANT_MEMBER_MANAGE_ANY'
ON CONFLICT (global_access_group_id, permission) DO NOTHING;

DELETE FROM direct_global_permission_grants WHERE permission = 'TENANT_MEMBER_MANAGE_ANY';
DELETE FROM global_access_group_permissions WHERE permission = 'TENANT_MEMBER_MANAGE_ANY';

-- TENANT_ACCESS_GROUP_MANAGE_ANY -> TENANT_ACCESS_GROUP_VIEW, TENANT_ACCESS_GROUP_CREATE, TENANT_ACCESS_GROUP_EDIT, TENANT_ACCESS_GROUP_DELETE
INSERT INTO direct_global_permission_grants (user_id, permission, created_at, created_by, updated_at, updated_by)
SELECT user_id, v.permission, created_at, created_by, updated_at, updated_by
FROM direct_global_permission_grants, LATERAL (
  VALUES ('TENANT_ACCESS_GROUP_VIEW'), ('TENANT_ACCESS_GROUP_CREATE'), ('TENANT_ACCESS_GROUP_EDIT'), ('TENANT_ACCESS_GROUP_DELETE')
) AS v(permission)
WHERE direct_global_permission_grants.permission = 'TENANT_ACCESS_GROUP_MANAGE_ANY'
ON CONFLICT (user_id, permission) DO NOTHING;

INSERT INTO global_access_group_permissions (global_access_group_id, permission, created_at, created_by, updated_at, updated_by)
SELECT global_access_group_id, v.permission, created_at, created_by, updated_at, updated_by
FROM global_access_group_permissions, LATERAL (
  VALUES ('TENANT_ACCESS_GROUP_VIEW'), ('TENANT_ACCESS_GROUP_CREATE'), ('TENANT_ACCESS_GROUP_EDIT'), ('TENANT_ACCESS_GROUP_DELETE')
) AS v(permission)
WHERE global_access_group_permissions.permission = 'TENANT_ACCESS_GROUP_MANAGE_ANY'
ON CONFLICT (global_access_group_id, permission) DO NOTHING;

DELETE FROM direct_global_permission_grants WHERE permission = 'TENANT_ACCESS_GROUP_MANAGE_ANY';
DELETE FROM global_access_group_permissions WHERE permission = 'TENANT_ACCESS_GROUP_MANAGE_ANY';

-- TENANT_PERMISSION_GRANT_MANAGE_ANY -> TENANT_PERMISSION_GRANT_VIEW, TENANT_PERMISSION_GRANT_CREATE, TENANT_PERMISSION_GRANT_DELETE
INSERT INTO direct_global_permission_grants (user_id, permission, created_at, created_by, updated_at, updated_by)
SELECT user_id, v.permission, created_at, created_by, updated_at, updated_by
FROM direct_global_permission_grants, LATERAL (
  VALUES ('TENANT_PERMISSION_GRANT_VIEW'), ('TENANT_PERMISSION_GRANT_CREATE'), ('TENANT_PERMISSION_GRANT_DELETE')
) AS v(permission)
WHERE direct_global_permission_grants.permission = 'TENANT_PERMISSION_GRANT_MANAGE_ANY'
ON CONFLICT (user_id, permission) DO NOTHING;

INSERT INTO global_access_group_permissions (global_access_group_id, permission, created_at, created_by, updated_at, updated_by)
SELECT global_access_group_id, v.permission, created_at, created_by, updated_at, updated_by
FROM global_access_group_permissions, LATERAL (
  VALUES ('TENANT_PERMISSION_GRANT_VIEW'), ('TENANT_PERMISSION_GRANT_CREATE'), ('TENANT_PERMISSION_GRANT_DELETE')
) AS v(permission)
WHERE global_access_group_permissions.permission = 'TENANT_PERMISSION_GRANT_MANAGE_ANY'
ON CONFLICT (global_access_group_id, permission) DO NOTHING;

DELETE FROM direct_global_permission_grants WHERE permission = 'TENANT_PERMISSION_GRANT_MANAGE_ANY';
DELETE FROM global_access_group_permissions WHERE permission = 'TENANT_PERMISSION_GRANT_MANAGE_ANY';
