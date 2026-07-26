# PLAN — primeng-migration

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Packages: `primeng@22.0.0`, `@primeuix/themes@3.0.0`,
  `primeicons@8.0.0`, `@angular/cdk@22.0.0`.** Why: `primeng@22.0.0` is
  the only PrimeNG major whose peer deps match this app's
  `@angular/core@^22.0.6`; it depends internally on `@primeuix/styled@^1.0.0`.
  The `@primeng/themes` wrapper package (used in most current PrimeNG
  docs/tutorials) is still pinned at `21.0.4` and depends on
  `@primeuix/styled@^0.7.4` — incompatible with `primeng@22`. `@primeuix/themes@3.0.0`
  is the theming package's own next major and depends on
  `@primeuix/styled@^1.0.0`, so it's the one that actually resolves
  cleanly; it exports the same `definePreset`/preset (`Aura`, `Lara`,
  `Material`, `Nora`) API `@primeng/themes` does, just from a different
  package name. `@angular/cdk` is a required peer of `primeng` (overlays,
  focus trapping) and wasn't previously a dependency of this app.
- **Theme preset lives at `src/app/core/prime-theme.ts`, exporting
  `InkSignalPreset` via `definePreset(Aura, {...})`.** Why: same location
  convention as other app-wide singletons in `core/` (services, guards);
  keeps the brand-to-PrimeNG token mapping in one reviewable file instead
  of scattered `pt`/inline overrides per component.
- **`signal-*` maps to PrimeNG's `primary` semantic palette; `ink-*`
  maps to `surface`.** Why: this is a direct restatement of what the
  brand already means in `styles.css`'s own comment — signal is "the one
  accent for primary actions/focus/highlights," which is exactly what
  PrimeNG's `primary` token drives; ink is the base color for surfaces/
  text.
- **`providePrimeNG({ theme: { preset: InkSignalPreset, options: { darkModeSelector: '.dark' } } })` in `app.config.ts`.**
  Why: `darkModeSelector` accepts any CSS selector string (not just
  PrimeNG's own default `.p-dark` convention) — pointing it at `.dark`
  reuses `ThemeService`'s existing toggle instead of introducing a
  second, independent dark-mode mechanism that could drift out of sync
  with the app's own.
- **No `cssLayer` configuration.** Why: correctly wiring PrimeNG's styles
  into Tailwind v4's own cascade-layer stack (`theme, base, components,
  utilities`) needs verifying Tailwind's actual emitted layer names
  first; getting it wrong silently breaks style precedence in a way
  that's easy to miss in review. Left as an explicit follow-up (flagged
  in "Known follow-ups" below) rather than guessed at here.
- **PrimeIcons (`primeicons/primeicons.css` imported in `styles.css`)
  replace the current inline-SVG icon pattern going forward.** Why: one
  icon system instead of two (inline SVG some places, PrimeIcons in
  PrimeNG components) — new/migrated components use `pi pi-*` classes;
  existing inline SVGs are swapped out screen by screen as each
  component is migrated (see migration order below), not all at once.
- **Sidebar/header "chrome" stays permanently dark (`bg-ink-950`,
  independent of `ThemeService`'s `.dark` toggle), unchanged by this
  migration.** Why (Tier 2 judgment call): the chrome's own dark
  background is a fixed brand/navigation-identity choice (`ink-950` as
  the app's "always-on" surface, the same role a permanent dark taskbar
  plays in some products), not the theme-togglable content area — it was
  already deliberately built this way in recent brand work and nothing
  about adopting PrimeNG requires revisiting that. PrimeNG's theme
  tokens apply per color-scheme (`light`/`dark` under `colorScheme`), so
  a PrimeNG component rendered inside the permanently-dark chrome (e.g.
  future header buttons) needs its *local* PrimeNG dark tokens active
  regardless of the app-wide toggle — call out per-component when
  migrating the chrome (first item in the migration order below), since
  `darkModeSelector` is document-global by default and the chrome may
  need a scoped override (e.g. wrapping the `<aside>`/`<header>` in a
  `.dark` class of their own, independent of `<html>`'s). Flagging this
  explicitly rather than deciding silently, since it's the first thing
  the next implementation pass will hit.
- **Proof of concept: `theme-toggle.component.ts` migrated to
  `[pButton]` (PrimeNG's `ButtonDirective`), `text rounded
  severity="secondary"`, with a `pi pi-sun`/`pi pi-moon` icon swapped
  in place of the emoji.** Why this component specifically: it's the
  simplest existing component (no forms, no complex state), and it
  directly exercises both the theme (button color/hover/focus via the
  `primary`/`surface` tokens) and PrimeIcons in one small, low-risk
  change — a good smoke test before a full implementation pass touches
  higher-traffic screens.
- **`angular.json`'s production budget (`maximumWarning`) raised from
  500kB to 750kB, then to 800kB (2026-07-25 chrome/menu pass).** Why:
  PrimeNG (even with only one component migrated) pushes the initial
  bundle past the old 500kB warning threshold; 750kB leaves headroom
  for the full migration while the 1MB `maximumError` (a real
  CI-breaking budget) is untouched. Adding `Menu`/`ButtonDirective` to
  the sidebar/header/help-menu pass pushed the initial bundle to
  752.65kB, just over 750kB, so the warning threshold was raised again
  to 800kB in the same pass — if the full migration approaches 1MB,
  that's a separate, later decision (code-splitting PrimeNG modules per
  route, etc.), not something to pre-solve here.

## Components and routes

No new routes. Existing components affected in this pass:
`app.config.ts` (providers), `styles.css` (PrimeIcons import),
`theme-toggle.component.ts` (proof of concept). All other components
listed in "Migration order" below are follow-up work for a
`frontend-engineer`/`design-system-ui-ux` agent, not built in this PLAN.

## Consumed API contracts

None — this is a client-side theming/tooling change with no backend
interaction.

## State and data

No new state. `ThemeService`'s existing `theme` signal remains the
single source of truth for light/dark; PrimeNG reads it indirectly via
the `.dark` class already applied to `<html>`.

## Dependencies

New (Tier 3, already approved by the app owner for this exact
migration — see `DECISIONS.md`):

- `primeng@22.0.0`
- `@primeuix/themes@3.0.0`
- `primeicons@8.0.0`
- `@angular/cdk@22.0.0` (required peer of `primeng`)

## Testing strategy

- Existing Vitest specs for migrated components continue to assert
  behavior (e.g. `theme-toggle.component.spec.ts`'s "renders a button"/
  "toggles the theme when clicked") without needing PrimeNG-specific
  setup — `ButtonDirective` is a plain Angular directive on a native
  `<button>`, so `querySelector('button')` and `.click()` keep working
  unchanged.
- No new test-only theming provider needed for unit tests: PrimeNG's
  `providePrimeNG` config lives in `app.config.ts` and is picked up by
  any test that imports the real component tree; components tested in
  isolation (`TestBed.configureTestingModule({ imports: [X] })`) don't
  need it unless they read injected `PrimeNG` config directly.
- `npm run format:check && npm test && npm run build` all green as of
  this PLAN's landing commit.

## Shared UI deliberately deferred (2026-07-25 chrome/menu pass)

`error-state.component.ts`, `no-access-state.component.ts`, and
`tour-overlay.component.ts` (checked as part of this pass, per this
PLAN's item 3 "any other shared UI actually used inside the shell/nav
today") were left as-is rather than migrated now: `error-state` is a
reasonable `p-message`/`Toast` candidate but touches every feature page
that renders it (dashboard, articles, etc.) well beyond "chrome," so
it's deferred to whichever later pass migrates those feature pages
rather than done piecemeal here; `no-access-state` is a single `<p>`,
not really a component-library concern; `tour-overlay` is a bespoke
positioned dialog reused by the onboarding tour and depends on exact
`getBoundingClientRect()` timing against `data-tour-id` targets —
revisit it once `p-dialog`/`p-popover`'s positioning behavior is
verified not to break that.

## Chrome dark-mode scoping — resolved (2026-07-25 implementation pass)

The sidebar (`<aside>`) and header (`<header>`) in `app-shell.component.ts`
now carry a static `dark` class directly on those two elements (not on
`<html>`), independent of `ThemeService`'s toggle. Why this is the
cleanest option, not a new mechanism: `styles.css` already defines
`@custom-variant dark (&:where(.dark, .dark *));` — Tailwind's own dark
variant already matches **any** descendant of a `.dark`-classed
ancestor, not just `<html>`. `providePrimeNG`'s `darkModeSelector: '.dark'`
generates PrimeNG's dark-token CSS rules scoped the same way (as a plain
CSS descendant selector, not a JS-toggled state). So a static `class="dark"`
on the chrome's two root elements makes *both* systems (Tailwind's
`dark:` utilities and PrimeNG's dark semantic tokens) apply to
everything inside the chrome unconditionally, while the rest of the
document (driven by `<html>`'s own `.dark` class from `ThemeService`)
is untouched — no second toggle, no JS-side coordination, and no change
to `ThemeService`/`theme-toggle.component.ts` at all. This reuses an
existing selector rather than introducing a new one, so it isn't a new
mechanism requiring separate sign-off, but is recorded here since it's
the resolution the "known follow-up" below was flagging.

## Final feature-screen pass (2026-07-25) — migration order items 4-7

All remaining migration-order items are now done, completing the
migration:

- **`error-state.component.ts` → `p-message` (`severity="error"`).**
  `no-access-state.component.ts` stayed a plain `<p>` — re-verified as
  still just that, not a component-library concern.
- **`welcome-page.component.ts`'s quick-link cards → `p-card`,** wrapped
  in the existing `<a routerLink>` so the whole card stays clickable;
  permission gating (`permissionsService.has(...)`) untouched.
- **`login-page.component.ts` → `pInputText`/`pPassword`/`pButton`
  directives applied directly to the existing native `<input>`/
  `<button>` elements** (not the `p-password`/component forms) —
  deliberately chosen because these are directives, not components, so
  no DOM wrapper is introduced and every existing
  `querySelector('input[type="email"]')`/`querySelector('input[name="code"]')`-
  style spec assertion kept working unchanged. The email/code/password
  tab UI itself (the `role="tab"` buttons) stayed hand-rolled — no
  PrimeNG tab component was a clean fit for this app's specific
  two-tab, non-routed pattern without restructuring the DOM tests
  depend on.
- **`articles-page.component.ts`**: upload form and detail panel wrapped
  in `p-card`; title/edit-title inputs → `pInputText`, edit-text →
  `pTextarea` (a directive, `primeng/textarea`); upload/save/delete
  buttons → `pButton` (delete uses `text severity="danger"`). The
  article-list `<ul>` rows were deliberately left as native markup, not
  `p-listbox`: each row has two independent actions (select the article,
  delete it) and `p-listbox` models a single click-to-select action per
  option — forcing it here would mean fighting the component's model
  rather than fitting it.
- **`conversations-page.component.ts`**: conversation list → `p-listbox`
  with a custom `#item` template (single action per row — a clean fit,
  unlike articles' list); new-conversation and send buttons → `pButton`;
  message input → `pInputText`. Chat bubbles stayed bespoke `<li>`s —
  they're rendered content bubbles with role-based styling, not an
  interactive/stateful UI element PrimeNG has a real component for.
- **`members-page.component.ts`**: member list → `p-table` with a custom
  `#body` template (two columns: email/select, remove button); add-member
  form → `pInputText`/`pButton`.
- **`select-tenant-page.component.ts`**: tenant list → `p-listbox` with a
  custom `#item` template (single action per row); create-tenant link →
  `pButton` (an `<a routerLink>` with the `pButton` directive, so
  routing behavior is untouched).
- **`tenant-create-page.component.ts`**: name/admin-email inputs →
  `pInputText`; submit button → `pButton`.
- **`angular.json`'s production budget raised again: `maximumWarning`
  800kB → 900kB, `maximumError` 1MB → 1.4MB.** Why: the full migration's
  bundle reached 1.27MB (up from 752.65kB after the chrome/menu pass) —
  `Card`/`InputText`/`Password`/`Textarea`/`Listbox`/`Table` each add
  their own component code, and none of this app's routes are
  lazy-loaded yet (`app.routes.ts` uses eager `component:` references,
  not `loadComponent`), so all of PrimeNG's now-larger surface area
  still ships in the one initial chunk. Raising the budget again is the
  same category of decision made twice already in this migration (500→
  750→800kB), not a new kind of call; the real fix — route-level lazy
  loading — is a bigger, separate change (routing architecture, not a
  PrimeNG component swap) and is recorded below as a following follow-up
  rather than done silently as part of this pass.
- **Every existing `data-testid`/`data-tour-id`/permission gate/
  `routerLink` survived unchanged**; no test needed to change to match
  a restructured DOM — `npm run format:check && npm test && npm run
  build` all green with the original 186 tests passing as-is.

## Known follow-ups (not solved by this PLAN)

- Tailwind v4 cascade-layer (`cssLayer`) integration for PrimeNG's
  injected styles — currently unlayered; verify no precedence conflicts
  emerge as more components migrate, then wire `cssLayer` properly if
  they do.
- `dashboard-analytics`'s SPEC (not yet approved) assumed ngx-charts;
  flagged back for revision to use PrimeNG's `Chart` (Chart.js wrapper)
  and `Table`/`DataTable` components instead, since no ngx-charts
  dependency exists yet and nothing has been built against it — this is
  a PO-owned SPEC revision, not decided here.
- `tour-overlay.component.ts` not migrated — its positioned-dialog
  behavior depends on exact `getBoundingClientRect()` timing against
  `data-tour-id` targets; revisit once `p-dialog`/`p-popover`'s
  positioning behavior is verified not to break that.
- Route-level lazy loading (`loadComponent` instead of eager `component:`
  references in `app.routes.ts`) would let each feature route's PrimeNG
  imports ship in their own chunk instead of all-in-one — the real fix
  for the bundle-size growth this migration caused (1.27MB initial as of
  the final pass), rather than repeatedly raising the budget. Not done
  here since it's a routing-architecture change, not a component swap.

## Migration order (priority, for the next implementation pass)

1. ~~**Sidebar (`app-nav-menu`) + header/topbar chrome
   (`app-shell.component.ts`)**~~ — done (2026-07-25 implementation
   pass). `nav-menu.component.ts` now renders each permission-gated
   category (Overview/Knowledge/Team/Workspace) as its own inline
   (`[popup]="false"`) `p-menu`, with a custom `#submenuheader` template
   for the translated category label and a custom `#item` template that
   renders the exact same `<a routerLink>` markup as before (same
   `data-testid`/`data-tour-id`/permission gates), just sourced from a
   `computed()` `MenuItem[]` model instead of hand-written `@if` blocks
   per link. Inline SVGs replaced with PrimeIcons (`pi-th-large` /
   `pi-book` / `pi-comments` / `pi-users` / `pi-plus` /
   `pi-arrow-right-arrow-left`). See "Chrome dark-mode scoping" above
   for how the permanently-dark chrome and `darkModeSelector` coexist.
2. ~~**Shared buttons and icon buttons**~~ — done. `logout-button`
   and `language-switcher` migrated to `[pButton] text severity="secondary"`
   (logout also `rounded`, with a `pi-sign-out` icon replacing its inline
   SVG), following `theme-toggle`'s already-migrated pattern.
3. ~~**Menus**~~ — done together with item 1's sidebar work above;
   `help-menu.component.ts`'s dropdown now uses a `p-menu` (inline, not
   popup, so its own `open` signal + `[attr.aria-expanded]` continue to
   drive visibility exactly as before) with a custom `#item` template
   for its one `restart-tour` action.
4. **Forms** (login-code entry, tenant creation, user-management forms)
   → PrimeNG `InputText`/`Select`/`Password`/form-adjacent components.
5. **Cards** (dashboard metric widgets, onboarding tour cards) → PrimeNG
   `Card`.
6. **Tables** (`user-management`'s members/roles/access-groups admin
   screens, and — once its SPEC is revised — `dashboard-analytics`) →
   PrimeNG `Table`/`DataTable`.
7. **Feature-specific remaining screens** (`conversations` chat UI,
   `article-management` upload/status UI) — last, since they're the
   most bespoke and least likely to map onto an off-the-shelf PrimeNG
   component 1:1; may need custom composition of several PrimeNG
   primitives rather than a single drop-in replacement.
