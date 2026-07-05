> **Reference example** — see notice in SPEC.md.

# PLAN — Tags management screen

## Architectural decisions

- Standalone component `TagsPageComponent` in `src/app/tags/`, routed in
  `app.routes.ts` as `/tags`.
- `TagsService` (`src/app/tags/tags.service.ts`) encapsulates HTTP calls via
  `HttpClient`, all using the relative path `/api/tags` (proxied in dev —
  see constitution, "Integration with the backend").
- Local component state with Angular Signals (`signal`/`computed`), no need
  for global state management for this feature.

## Components and routes

- `TagsPageComponent`: page with list + creation form.
- Route `/tags` → `TagsPageComponent` (lazy-loaded).

## Consumed API contracts

- `GET /api/tags` → `TagResponse[]` (`{ id: number; name: string }`)
- `POST /api/tags` → body `{ name: string }` → `TagResponse` | `400` error
  with body `{ message: string }`
- `DELETE /api/tags/{id}` → `204` | `404`

## State and data

- `TagsService.tags = signal<TagResponse[]>([])`
- `TagsService.loading = signal<boolean>(false)` for REQ-5.
- Reactive form (`FormGroup`) with a single `name` field
  (`Validators.required`, `Validators.maxLength(50)`).

## Dependencies

None new — uses `@angular/common/http` (already part of Angular) and
`@angular/forms`, already present in `package.json`.

## Testing strategy

- `tags.service.spec.ts`: tests HTTP calls with `HttpTestingController`,
  covers REQ-2/REQ-3 success and 400 error.
- `tags-page.component.spec.ts`: tests list rendering (REQ-1), form
  submission (REQ-2), error display (REQ-3), removal (REQ-4), and disabled
  state while loading (REQ-5).
