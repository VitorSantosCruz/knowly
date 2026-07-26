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
  500kB to 750kB.** Why: PrimeNG (even with only one component migrated)
  pushes the initial bundle past the old 500kB warning threshold; 750kB
  leaves headroom for the full migration while the 1MB `maximumError`
  (a real CI-breaking budget) is untouched — if the full migration
  approaches that, it's a separate, later decision (code-splitting
  PrimeNG modules per route, etc.), not something to pre-solve here.

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

## Known follow-ups (not solved by this PLAN)

- Tailwind v4 cascade-layer (`cssLayer`) integration for PrimeNG's
  injected styles — currently unlayered; verify no precedence conflicts
  emerge as more components migrate, then wire `cssLayer` properly if
  they do.
- Whether the permanently-dark chrome needs its own scoped dark-mode
  context for embedded PrimeNG components (see "chrome stays dark"
  decision above) — first thing to resolve when migrating the sidebar/
  header itself.
- `dashboard-analytics`'s SPEC (not yet approved) assumed ngx-charts;
  flagged back for revision to use PrimeNG's `Chart` (Chart.js wrapper)
  and `Table`/`DataTable` components instead, since no ngx-charts
  dependency exists yet and nothing has been built against it — this is
  a PO-owned SPEC revision, not decided here.

## Migration order (priority, for the next implementation pass)

1. **Sidebar (`app-nav-menu`) + header/topbar chrome
   (`app-shell.component.ts`)** — most visually prominent, most
   complained-about; also where the "chrome stays permanently dark"
   scoping question (above) needs to be resolved first, since every
   later item touches components that live inside this shell.
2. **Shared buttons and icon buttons** (`logout-button.component.ts`,
   `help-menu.component.ts`, any remaining icon-only buttons) — small,
   low-risk, high-reuse; establishes the `[pButton]`/PrimeIcons pattern
   the rest of the migration follows (same reasoning as this PLAN's
   `theme-toggle` proof of concept).
3. **Menus** (`nav-menu.component.ts`'s link rendering, `help-menu`,
   language switcher) → PrimeNG `Menu`/`Menubar`/`TieredMenu` as
   appropriate.
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
