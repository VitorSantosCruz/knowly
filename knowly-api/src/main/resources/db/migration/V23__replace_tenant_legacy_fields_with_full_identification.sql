-- tenant-creation: full company identification, replacing the unused,
-- never-populated speculative columns from V17. See
-- specify/features/tenant-creation/PLAN.md ("Data schema").

DROP INDEX IF EXISTS ux_tenants_cnpj;
DROP INDEX IF EXISTS ux_tenants_inscricao_estadual;
ALTER TABLE tenants DROP COLUMN cnpj;
ALTER TABLE tenants DROP COLUMN razao_social;
ALTER TABLE tenants DROP COLUMN nome_fantasia;
ALTER TABLE tenants DROP COLUMN inscricao_estadual;
ALTER TABLE tenants_aud DROP COLUMN cnpj;
ALTER TABLE tenants_aud DROP COLUMN razao_social;
ALTER TABLE tenants_aud DROP COLUMN nome_fantasia;
ALTER TABLE tenants_aud DROP COLUMN inscricao_estadual;

-- add new columns, nullable first (safe-add pattern)
ALTER TABLE tenants ADD COLUMN legal_name VARCHAR(255);
ALTER TABLE tenants ADD COLUMN tax_id VARCHAR(32);
ALTER TABLE tenants ADD COLUMN country VARCHAR(100);
ALTER TABLE tenants ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE tenants ADD COLUMN contact_phone VARCHAR(30);
ALTER TABLE tenants ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE tenants ADD COLUMN street VARCHAR(255);
ALTER TABLE tenants ADD COLUMN number VARCHAR(20);
ALTER TABLE tenants ADD COLUMN complement VARCHAR(100);
ALTER TABLE tenants ADD COLUMN neighborhood VARCHAR(100);
ALTER TABLE tenants ADD COLUMN city VARCHAR(100);
ALTER TABLE tenants ADD COLUMN state VARCHAR(100);

-- backfill any pre-existing row (bootstrap tenant, dev/CI leftovers) with
-- sentinel placeholders -- no real identification data exists for these
-- rows, and this feature does not invent one; a future tenant-crud edit
-- can correct the bootstrap tenant's placeholder values like any other
-- tenant's fields (legalName/contactEmail/etc. are all editable there --
-- taxId is not, so the bootstrap tenant's placeholder taxId is
-- permanent unless deleted and recreated, an accepted pre-launch tradeoff)
UPDATE tenants SET
  legal_name = COALESCE(legal_name, name),
  tax_id = COALESCE(tax_id, 'PENDING-' || id),
  country = COALESCE(country, 'BR'),
  contact_email = COALESCE(contact_email, 'unset@example.invalid'),
  contact_phone = COALESCE(contact_phone, '0000000000'),
  postal_code = COALESCE(postal_code, '00000000'),
  street = COALESCE(street, 'unset'),
  number = COALESCE(number, 'unset'),
  neighborhood = COALESCE(neighborhood, 'unset'),
  city = COALESCE(city, 'unset'),
  state = COALESCE(state, 'unset')
WHERE legal_name IS NULL OR tax_id IS NULL OR country IS NULL
   OR contact_email IS NULL OR contact_phone IS NULL OR postal_code IS NULL
   OR street IS NULL OR number IS NULL OR neighborhood IS NULL
   OR city IS NULL OR state IS NULL;

ALTER TABLE tenants ALTER COLUMN legal_name SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN tax_id SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN country SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN contact_email SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN contact_phone SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN postal_code SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN street SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN number SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN neighborhood SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN city SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN state SET NOT NULL;

CREATE UNIQUE INDEX ux_tenants_tax_id ON tenants (tax_id);

ALTER TABLE tenants_aud ADD COLUMN legal_name VARCHAR(255);
ALTER TABLE tenants_aud ADD COLUMN tax_id VARCHAR(32);
ALTER TABLE tenants_aud ADD COLUMN country VARCHAR(100);
ALTER TABLE tenants_aud ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE tenants_aud ADD COLUMN contact_phone VARCHAR(30);
ALTER TABLE tenants_aud ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE tenants_aud ADD COLUMN street VARCHAR(255);
ALTER TABLE tenants_aud ADD COLUMN number VARCHAR(20);
ALTER TABLE tenants_aud ADD COLUMN complement VARCHAR(100);
ALTER TABLE tenants_aud ADD COLUMN neighborhood VARCHAR(100);
ALTER TABLE tenants_aud ADD COLUMN city VARCHAR(100);
ALTER TABLE tenants_aud ADD COLUMN state VARCHAR(100);
