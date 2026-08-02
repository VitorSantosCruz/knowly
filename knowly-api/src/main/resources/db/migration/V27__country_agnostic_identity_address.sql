-- identity-profile-model-v2 amendment (2026-08-02, "country-agnostic identity/address model"):
-- cpf -> tax_id (country-conditional checksum, gated at the service layer on country_code),
-- new user_profiles.country_code, addresses restructured to a country-agnostic shape
-- (address_line1/address_line2/city/state_region/postal_code/country_code), and E.164 backfill
-- for PHONE/WHATSAPP contacts. See
-- specify/features/identity-profile-model-v2/PLAN.md ("Handoff to data-architect-dba").
--
-- Runs after V26 (rg/birth_date already dropped from user_profiles/user_profiles_aud/
-- profile_edit_requests) -- this migration does not touch rg-related columns, they're gone.
--
-- Decisions made here (not finalized by PLAN, see PLAN's "Not decided here" list):
--  1. cpf/cpf_blind_index -> tax_id/tax_id_blind_index is a straight RENAME COLUMN, no
--     re-encryption. CpfRgEncryptionConverter (see its class doc) is a symmetric AES-256-GCM
--     transform over an opaque string -- the ciphertext encodes nothing about which column or
--     field it came from, so renaming the column changes no bytes on disk. Confirmed by reading
--     the converter's actual implementation before writing this migration.
--  2. user_profiles.country_code is added NULLABLE, not NOT NULL, even though every existing row
--     is backfilled to 'BR' below. Reasoning: user_profiles keeps its established "eager row,
--     nullable until filled" posture (REQ-1) for every other field (full_name, tax_id,
--     avatar_url all nullable) -- and PLAN's own architectural-decision text for this amendment
--     explicitly gives NULL country_code a meaning UserProfileService.requireValidTaxId already
--     depends on ("not yet selected" => skip the BR-only checksum gate). Forcing NOT NULL would
--     either break that semantics or require defaulting every *new* user to 'BR' by fiat, which
--     is a product decision this migration has no basis to make on its own. Existing rows are
--     still backfilled to 'BR' below (a factual, not speculative, statement about this system's
--     Brazil-only history), just not enforced going forward.
--  3. addresses.address_line1 concatenation for the fold: `{logradouro}, {numero}` then
--     `, {complemento}` appended only if complemento is present. Chosen as the simplest
--     unambiguous separator that keeps every original token round-trippable from the data
--     migration's own audit trail (this repo's git history + this file), not because any other
--     separator was ruled out for a stronger reason -- PLAN explicitly left this to
--     data-architect-dba's judgment.
--  4. profile_edit_requests' old proposed_cep/logradouro/numero/complemento/bairro/cidade/
--     estado/pais columns are dropped outright, not migrated to the new proposed_* shape --
--     every PENDING row is cancelled by this same migration (same precedent V18 already
--     established), so there is no live proposed-address data to preserve through the fold.
--  5. _aud tables (user_profiles_aud/addresses_aud) get the same column renames/drops/adds as
--     their base tables. Historical revision rows lose their old-shape column values on the
--     dropped columns (address fold data, cpf column name) -- this is the same accepted,
--     documented Envers-audit gap already on record for pure-SQL migration writes (see this
--     repo's CLAUDE.md conventions): a schema-shape change on an audited entity's columns isn't
--     itself re-audited retroactively, only new writes after this migration are.

-- cancel any in-flight PENDING request before its shape changes further (same precedent as V18).
UPDATE profile_edit_requests SET status = 'CANCELLED', resolved_at = now()
  WHERE status = 'PENDING';

-- ============================================================================
-- 1. user_profiles: cpf -> tax_id rename, new country_code
-- ============================================================================

ALTER TABLE user_profiles RENAME COLUMN cpf TO tax_id;
ALTER TABLE user_profiles RENAME COLUMN cpf_blind_index TO tax_id_blind_index;
ALTER INDEX ux_user_profiles_cpf_blind_index RENAME TO ux_user_profiles_tax_id_blind_index;

ALTER TABLE user_profiles ADD COLUMN country_code VARCHAR(2);
UPDATE user_profiles SET country_code = 'BR';

ALTER TABLE user_profiles_aud RENAME COLUMN cpf TO tax_id;
ALTER TABLE user_profiles_aud RENAME COLUMN cpf_blind_index TO tax_id_blind_index;
ALTER TABLE user_profiles_aud ADD COLUMN country_code VARCHAR(2);

-- ============================================================================
-- 2. addresses: restructure to the country-agnostic shape
-- ============================================================================

ALTER TABLE addresses ADD COLUMN address_line1 VARCHAR(255);
ALTER TABLE addresses ADD COLUMN address_line2 VARCHAR(100);
ALTER TABLE addresses ADD COLUMN city VARCHAR(100);
ALTER TABLE addresses ADD COLUMN state_region VARCHAR(100);
ALTER TABLE addresses ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE addresses ADD COLUMN country_code VARCHAR(2);

-- fold logradouro + numero + complemento -> address_line1 (decision 3 above); bairro ->
-- address_line2 directly; cep/cidade/estado -> postal_code/city/state_region directly;
-- country_code backfilled unconditionally to 'BR' (every pre-existing row is Brazilian by
-- construction -- pais defaulted to 'Brasil' and no other value was ever collectable).
UPDATE addresses SET
  address_line1 = logradouro || ', ' || COALESCE(numero, '') || COALESCE(', ' || complemento, ''),
  address_line2 = bairro,
  city = cidade,
  state_region = estado,
  postal_code = cep,
  country_code = 'BR';

ALTER TABLE addresses ALTER COLUMN address_line1 SET NOT NULL;
ALTER TABLE addresses ALTER COLUMN city SET NOT NULL;
ALTER TABLE addresses ALTER COLUMN postal_code SET NOT NULL;
ALTER TABLE addresses ALTER COLUMN country_code SET NOT NULL;
-- address_line2/state_region stay nullable per the new shape.

-- drop the old Brazil-only columns; their CHECK constraints (cep/estado format) are dropped
-- automatically along with the column they reference.
ALTER TABLE addresses
  DROP COLUMN cep,
  DROP COLUMN logradouro,
  DROP COLUMN numero,
  DROP COLUMN complemento,
  DROP COLUMN bairro,
  DROP COLUMN cidade,
  DROP COLUMN estado,
  DROP COLUMN pais;

ALTER TABLE addresses_aud ADD COLUMN address_line1 VARCHAR(255);
ALTER TABLE addresses_aud ADD COLUMN address_line2 VARCHAR(100);
ALTER TABLE addresses_aud ADD COLUMN city VARCHAR(100);
ALTER TABLE addresses_aud ADD COLUMN state_region VARCHAR(100);
ALTER TABLE addresses_aud ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE addresses_aud ADD COLUMN country_code VARCHAR(2);
ALTER TABLE addresses_aud
  DROP COLUMN cep,
  DROP COLUMN logradouro,
  DROP COLUMN numero,
  DROP COLUMN complemento,
  DROP COLUMN bairro,
  DROP COLUMN cidade,
  DROP COLUMN estado,
  DROP COLUMN pais;

-- ============================================================================
-- 3. profile_edit_requests: same rename/restructure on the proposed_* mirror
-- ============================================================================

ALTER TABLE profile_edit_requests RENAME COLUMN proposed_cpf TO proposed_tax_id;
ALTER TABLE profile_edit_requests ADD COLUMN proposed_country_code VARCHAR(2);

ALTER TABLE profile_edit_requests ADD COLUMN proposed_address_line1 VARCHAR(255);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_address_line2 VARCHAR(100);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_city VARCHAR(100);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_state_region VARCHAR(100);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_postal_code VARCHAR(20);

-- old shape dropped outright (decision 4 above) -- every PENDING row was already cancelled above,
-- so there is no live proposed-address data to fold forward.
ALTER TABLE profile_edit_requests
  DROP COLUMN proposed_cep,
  DROP COLUMN proposed_logradouro,
  DROP COLUMN proposed_numero,
  DROP COLUMN proposed_complemento,
  DROP COLUMN proposed_bairro,
  DROP COLUMN proposed_cidade,
  DROP COLUMN proposed_estado,
  DROP COLUMN proposed_pais;

-- ============================================================================
-- 4. contacts: E.164 backfill for PHONE/WHATSAPP (REQ-3c tightened regex requires a leading '+')
-- ============================================================================

UPDATE contacts SET value = '+55' || value
  WHERE type IN ('PHONE', 'WHATSAPP') AND value !~ '^\+';
