-- tenant-crud: soft delete for tenants (REQ-8 through REQ-12). See
-- specify/features/tenant-crud/PLAN.md ("Data schema").

ALTER TABLE tenants ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE tenants_aud ADD COLUMN deleted_at TIMESTAMP;

-- tenant-creation/PLAN.md's V23 created ux_tenants_tax_id as a plain,
-- unconditional unique index; replace it with a partial one so a
-- soft-deleted tenant's taxId can be reused by a later, independent
-- tenant creation (REQ-12).
DROP INDEX IF EXISTS ux_tenants_tax_id;
CREATE UNIQUE INDEX ux_tenants_tax_id ON tenants (tax_id) WHERE deleted_at IS NULL;
