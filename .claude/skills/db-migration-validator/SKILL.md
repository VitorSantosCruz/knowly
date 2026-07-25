---
name: db-migration-validator
description: Use before writing any file under src/main/resources/db/migration/, before adding a JPA entity with a schema implication, or when asked to change the database schema. Triggers on "cria uma migration", "adiciona uma coluna/tabela", "preciso mudar o schema".
---

# db-migration-validator

Validates and scaffolds Flyway migrations for **knowly**'s
PostgreSQL/pgvector database, per the conventions already established
across `V1`–`V14`.

## Rules & anti-patterns

- **DO** check `ls src/main/resources/db/migration/` for the actual
  current max version number before naming the file — never guess or
  reuse a number.
- **STRICTLY PROHIBITED**: editing an already-applied migration file.
  A fix is always a *new*, higher-numbered migration — Flyway's
  versioning integrity depends on this, and it's also what makes a
  migration's history an honest audit trail.
- **DO** add the Envers `_aud` mirror table in the **same** migration
  that creates any `@Audited` entity's base table (see `V3`+`V4`'s
  pairing as the reference, though later features folded both into one
  file — check the most recent example, not the oldest).
- **DO** give every table `created_at`/`created_by`/`updated_at`/
  `updated_by` columns matching JPA's auditing annotations —
  `TIMESTAMPTZ NOT NULL DEFAULT now()` for the two dates, `VARCHAR(255)
  NOT NULL` for the two actors.
- **DO** back every "must be unique" business rule with a real
  `UNIQUE` constraint or unique index — see `ux_users_email_lower` — not
  just an application-level `findBy...`-then-insert check, which races
  under concurrent requests.
- **STRICTLY PROHIBITED**: a destructive operation (`DROP TABLE`,
  `DROP COLUMN`, a data-lossy type narrowing) without first stopping and
  getting explicit user confirmation — this is Tier 3 per
  `DECISIONS.md` ("anything hard to reverse: schema changes that lose
  data"), not a routine migration to write and move on.
- **DO** pin any new tenant-owned entity to the existing Hibernate
  `@Filter` mechanism (`@Filter(name = TenantFilter.NAME, condition =
  "tenant_id = :" + TenantFilter.PARAMETER)`) — never a manual
  `tenant_id` WHERE clause anywhere in the service layer instead.
- **DO** verify any migration that reads a Flyway placeholder
  (`spring.flyway.placeholders.*`) actually resolves from a **bare**
  `${VAR}` (required) or `${VAR:default}` (has a real default) in
  `application.yaml` — never `${VAR:?message}`, which Spring resolves
  as a literal default string instead of failing (see `DECISIONS.md`).

## Execution steps

1. `ls src/main/resources/db/migration/` — confirm the next version
   number.
2. Design the table(s): columns, types, constraints, indexes, matching
   the "every table gets these 4 audit columns" rule above.
3. If the entity is `@Audited` (Envers): design the mirror `_aud` table
   in the same migration (nullable columns except PK/rev, `PRIMARY KEY
   (id, rev)`, `rev BIGINT NOT NULL REFERENCES revinfo (rev)`).
4. If this is a data migration (not just DDL) touching existing rows:
   confirm with the user before writing it — this is exactly the
   "hard to reverse" Tier 3 case.
5. Write the `.sql` file (template below).
6. Write/update the matching JPA entity with the equivalent annotations
   — schema and entity must be added in the same task, never one
   without the other.
7. Run the actual integration test suite against the real Testcontainers
   Postgres (`./mvnw test -Dtest=<RelevantTest>`) — a migration that
   only "looks right" as SQL is not verified. Check the real exit code,
   not a piped one (see `ci-pipeline-guard` skill).

## Template

```sql
-- <one-line description of what this migration does and why>
CREATE TABLE <table_name> (
  id BIGSERIAL PRIMARY KEY,
  <business_columns...>,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX ux_<table_name>_<column> ON <table_name> (<column>);

-- Envers audit mirror, only if the entity is @Audited
CREATE TABLE <table_name>_aud (
  id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  <business_columns, all nullable>,
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);
```
