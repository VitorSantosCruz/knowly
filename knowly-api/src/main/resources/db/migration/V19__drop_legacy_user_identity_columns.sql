-- identity-profile-model-v2 cleanup (TASKS.md 27): V17/V18 already retrofitted personal data
-- onto user_profiles/addresses/contacts and confirmed UserProfileService/ProfileEditRequestService
-- read/write those tables exclusively. This drops the legacy flat columns V17 added to
-- users/users_aud, now unused by any code path, per PLAN.md's "Deviations" section (this migration
-- was deliberately deferred until the new code path was verified running in production; the
-- product owner confirmed that gate on 2026-07-30).

-- unique indexes depend on these columns; drop them explicitly for clarity rather than relying on
-- ALTER TABLE ... DROP COLUMN's implicit cascade.
DROP INDEX IF EXISTS ux_users_address;
DROP INDEX IF EXISTS ux_users_phone;
DROP INDEX IF EXISTS ux_users_rg_blind_index;
DROP INDEX IF EXISTS ux_users_cpf_blind_index;

ALTER TABLE users
  DROP COLUMN full_name,
  DROP COLUMN address,
  DROP COLUMN rg,
  DROP COLUMN cpf,
  DROP COLUMN phone,
  DROP COLUMN rg_blind_index,
  DROP COLUMN cpf_blind_index;

ALTER TABLE users_aud
  DROP COLUMN full_name,
  DROP COLUMN address,
  DROP COLUMN rg,
  DROP COLUMN cpf,
  DROP COLUMN phone,
  DROP COLUMN rg_blind_index,
  DROP COLUMN cpf_blind_index;
