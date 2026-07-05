> **Reference example** — see notice in SPEC.md.

# PLAN — Tags CRUD

## Architectural decisions

- New package `br.com.conectabyte.knowly.tag` with `TagController`,
  `TagService`, `TagRepository`, `Tag` (JPA entity), `TagRequestDto`,
  `TagResponseDto`.
- Validation via Bean Validation (`spring-boot-starter-validation`) directly
  on `TagRequestDto` (`@NotBlank`, `@Size(max = 50)`).
- Case-insensitive uniqueness checked in `TagService` before persisting
  (lookup by lowercased `name`) — not relying solely on a DB constraint so
  we can respond 400 with a clear message.

## Data schema

Flyway migration `V1__create_tags_table.sql`:

```sql
CREATE TABLE tags (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_tags_name_lower ON tags (LOWER(name));
```

## API contracts

- `POST /api/tags` — body `{ "name": string }` → `201` `{ "id": number, "name": string }`
- `GET /api/tags` → `200` `[{ "id": number, "name": string }, ...]`
- `DELETE /api/tags/{id}` → `204` (no body) | `404`

## Dependencies

None new — uses `spring-boot-starter-data-jpa`, `-validation`, `-webmvc`,
`-security`, and `-flyway`, already present in `pom.xml`.

## Package/file structure

```
src/main/java/br/com/conectabyte/knowly/tag/
  Tag.java
  TagRepository.java
  TagService.java
  TagController.java
  dto/TagRequestDto.java
  dto/TagResponseDto.java
src/main/resources/db/migration/V1__create_tags_table.sql
src/test/java/br/com/conectabyte/knowly/tag/
  TagServiceTest.java                (unit)
  TagControllerIntegrationTest.java  (Testcontainers, via TestcontainersConfiguration)
```

## Testing strategy

- `TagServiceTest`: unit test, mocks `TagRepository`, covers REQ-1, REQ-2
  (duplication).
- `TagControllerIntegrationTest`: boots the full context with Postgres via
  Testcontainers, covers REQ-1 through REQ-5 end to end (HTTP → database).
