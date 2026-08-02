-- LGPD data-minimization: remove RG (Brazil-only secondary ID document, redundant next to
-- cpf/taxId) and birth_date entirely, per product decision 2026-08-02. Both fields were added by
-- V17/V18 (identity-profile-model / identity-profile-model-v2). No documented legal need for
-- either field; see updated SPEC.md files for identity-profile-model-v2,
-- mandatory-complete-profile, user-profile-v2, bootstrap-profile-completion.
--
-- rg participates in a blind-index uniqueness check (ux_user_profiles_rg_blind_index) and a
-- CHECK constraint tying rg/rg_blind_index nullability together; birth_date has neither (plain
-- DATE column, no encryption/blind-index, no uniqueness constraint). Dropping rg/rg_blind_index
-- in the same ALTER TABLE statement as their dependent CHECK constraint and unique index lets
-- Postgres cascade both automatically -- verified against a scratch Postgres 16 instance before
-- writing this migration.

-- unique index depends on user_profiles.rg_blind_index; drop it explicitly for clarity rather
-- than relying on the implicit cascade from DROP COLUMN below (same convention as V19).
DROP INDEX IF EXISTS ux_user_profiles_rg_blind_index;

ALTER TABLE user_profiles
  DROP COLUMN rg,
  DROP COLUMN rg_orgao_emissor,
  DROP COLUMN rg_blind_index,
  DROP COLUMN birth_date;

ALTER TABLE user_profiles_aud
  DROP COLUMN rg,
  DROP COLUMN rg_orgao_emissor,
  DROP COLUMN rg_blind_index,
  DROP COLUMN birth_date;

-- profile_edit_requests: proposed_rg/proposed_rg_orgao_emissor/proposed_birth_date, added by
-- V17/V18 for the same fields on the proposal side.
ALTER TABLE profile_edit_requests
  DROP COLUMN proposed_rg,
  DROP COLUMN proposed_rg_orgao_emissor,
  DROP COLUMN proposed_birth_date;
