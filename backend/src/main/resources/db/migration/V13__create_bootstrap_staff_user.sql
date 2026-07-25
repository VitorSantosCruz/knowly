-- Unblocks the very first login on a fresh deployment: a staff user with no
-- password of any kind, logged in to via the existing login-code flow. See
-- specify/features/staff-bootstrap-user/SPEC.md.
INSERT INTO users (email, global_role, created_by, updated_by)
VALUES ('${bootstrap-staff-email}', 'STAFF', 'system', 'system');
