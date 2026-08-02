# TASKS — tenant-creation (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. Migration `V23__replace_tenant_legacy_fields_with_full_identification.sql`
      (PLAN.md's "Data schema"): drop `cnpj`/`razao_social`/
      `nome_fantasia`/`inscricao_estadual` from `tenants`/`tenants_aud`;
      add the eleven new columns nullable, backfill any pre-existing row
      with sentinel placeholders, set `NOT NULL`, create
      `ux_tenants_tax_id`. Test: `V23MigrationTest` (mirrors
      `V17MigrationTest`'s shape) — seed a pre-migration row with only
      `name` set, run the migration, assert sentinel backfill + `NOT
      NULL` on all eleven columns + dropped columns gone from both
      tables.
- [x] 2. `Tenant.java`: drop the four legacy fields, add the eleven new
      `@Column`-mapped `String` fields. Plain mapping/getter-setter test
      only, no behavior yet (mirrors existing entity test conventions).
- [x] 3. `TaxIdValidator`/`@ValidTaxId` (new
      `br.com.conectabyte.knowly.tenancy.validation` package). Test
      (`TaxIdValidatorTest`, unit, no Spring context): Brazil + 14-digit
      unpunctuated passes; Brazil + punctuated-but-14-digits passes;
      Brazil + wrong digit count fails; non-Brazil + any non-empty
      string passes; blank `taxId` fails regardless of country (Red
      first, then implement).
- [x] 4. `CreateTenantRequestDto`/`AddressDto` (PLAN.md's "API
      contracts" — nested `address`, `@ValidTaxId` on `taxId`, `@Email`/
      `@NotBlank` on every other mandatory field, `complement`
      unannotated). Test: bean-validation round trip — every mandatory
      field missing individually triggers a violation on that exact
      field; a fully valid instance has zero violations.
- [x] 5. `TenantAlreadyExistsException` (new, 409) +
      `TenancyExceptionHandler` handler method. Test: handler maps the
      exception to 409 with the existing `TenancyErrorResponseDto`
      shape.
- [x] 6. `TenantService#persistMemberProfile` extraction (PLAN.md's
      amendment): pull the post-authorization `UserProfile`/`Address`/
      `Contact`/`TenantMembership` persistence logic already used by
      `addMember` (per `mandatory-complete-profile/PLAN.md`) into a
      private helper reusable by both `addMember` and the new
      `createTenant`. Test: `addMember`'s existing test suite stays
      green unchanged (pure refactor, no behavior change) — confirms
      the extraction didn't alter `addMember`'s contract.
- [x] 7. `TenantService#createTenant` rewritten to the new signature
      (`CreateTenantRequestDto`), `@Transactional`, builds `Tenant` +
      first `TenantMembership` (role defaulting to `MEMBER_ADMIN`) via
      the task-6 helper, catches `DataIntegrityViolationException` on
      the `tax_id` unique index and rethrows `TenantAlreadyExistsException`,
      gains `@AuditLog(action = "tenant.create", resourceType =
      "Tenant")`. Unit tests (`TenantServiceTest`): every field persists
      as submitted; `taxId` collision → `TenantAlreadyExistsException`,
      no row saved (mocked repository); default role `MEMBER_ADMIN`
      applied when `role` omitted from the request.
- [x] 8. `TenantController#createTenant` updated to the new DTO-based
      call; `TenantRepository`'s search `@Query` updated to
      `name`/`legalName`/`taxId`; `TenantSummaryDto` field set updated
      (drop legacy fields, add `legalName`/`taxId`). Update the existing
      fixtures that reference the dropped fields/old `createTenant`
      signature: `TenantSearchRepositoryTest`, `TenantSummaryDtoTest`,
      `TenantPaginationSearchIntegrationTest`,
      `IdentityUniquenessIntegrationTest`, `TenantManagementIntegrationTest`,
      `TenantSessionIntegrationTest` — same test intent, new field/
      column names, no coverage lost.
- [x] 9. Integration test pass (`TenantManagementIntegrationTest` or a
      new focused class) covering every SPEC acceptance criterion end to
      end: full valid submission succeeds and stored data matches
      (REQ-1); missing mandatory field → 400 naming it (REQ-3); malformed
      `contactEmail` → 400 (REQ-3); duplicate `taxId` → 409, no row
      created (REQ-4/REQ-5); Brazil + non-14-digit `taxId` → 400 (REQ-6);
      non-Brazil + any non-empty `taxId` → 200 (REQ-6); non-staff caller
      → 403 (REQ-7, existing `tenancy` REQ-10 coverage re-run against the
      new DTO); tenant creation audit-logged on both success and
      rejection (REQ-8); first-member `TenantMembership` created with
      `MEMBER_ADMIN` role in the same transaction as the tenant
      (2026-08-02 amendment); any failure in the first-user-profile
      portion of the request rolls back the whole call — no orphaned
      `Tenant` row (same amendment).
- [x] 10. Full acceptance-criteria pass: re-verify every checkbox in
      SPEC.md's "Acceptance criteria" explicitly against the finished
      implementation; update PLAN.md's "Deviations from this PLAN"
      section with anything that changed during implementation.
- [x] 11. Run `./mvnw spotless:apply && ./mvnw verify` and confirm the
      full suite is green before committing.
