> **Reference example** — see notice in SPEC.md.

# TASKS — Tags CRUD

- [ ] 1. Create migration `V1__create_tags_table.sql` (`tags` table +
      case-insensitive unique index on `name`).
- [ ] 2. Create `Tag` entity, `TagRepository` (Spring Data JPA) with an
      `existsByNameIgnoreCase(String name)` method.
- [ ] 3. Write `TagServiceTest` covering REQ-1 and REQ-2 (Red).
- [ ] 4. Implement `TagService` (create, list, remove) until
      `TagServiceTest` passes (Green).
- [ ] 5. Write `TagControllerIntegrationTest` covering REQ-1 through REQ-5
      (Red).
- [ ] 6. Implement `TagController` + DTOs until
      `TagControllerIntegrationTest` passes (Green).
- [ ] 7. Run `./mvnw verify` (Spotless + full suite) and confirm it's green.
- [ ] 8. Update `PLAN.md` if any decision changed during implementation.
