# PLAN — primeng-removal

> The how. Translates the SPEC into concrete technical decisions.
> References `DECISIONS.md`'s two consecutive entries ("Frontend adopts
> PrimeNG as its component library (2026-07-25)" and "Frontend drops
> PrimeNG, reverts to pure Tailwind + Angular (2026-07-26)") — the
> decision to remove PrimeNG is already made by the app owner; this PLAN
> only covers the technical how.

There is no separate SPEC.md for this feature: the "spec" is the
`DECISIONS.md` entry itself (Tier 3, owner-confirmed), which already
states the desired end state (pure Tailwind + Angular standalone
components, Lucide icons) and explicitly defers the component-by-
component removal order and replacement patterns to this PLAN.

## Confirmed scope (grepped, not assumed)

20 files import from `primeng/*`, `primeicons`, or `@primeuix/themes`,
using exactly these PrimeNG modules:

| PrimeNG import                                             | Files                                                                                                                                                                                                                             |
| ---------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ButtonDirective` (`primeng/button`)                       | logout-button, language-switcher, help-menu, theme-toggle, nav-menu (via help-menu only, not nav-menu itself), export-button, members-page, select-tenant-page, conversations-page, tenant-create-page, login-page, articles-page |
| `Menu`/`MenuItem` (`primeng/menu`, `primeng/api`)          | help-menu, nav-menu                                                                                                                                                                                                               |
| `Message` (`primeng/message`)                              | error-state                                                                                                                                                                                                                       |
| `UIChart` (`primeng/chart`)                                | conversations-activity-chart, message-split-chart, metric-tile                                                                                                                                                                    |
| `SelectButton` (`primeng/selectbutton`)                    | period-filter                                                                                                                                                                                                                     |
| `InputText`/`Table` (`primeng/inputtext`, `primeng/table`) | top-articles-table, members-page                                                                                                                                                                                                  |
| `Listbox` (`primeng/listbox`)                              | select-tenant-page, conversations-page                                                                                                                                                                                            |
| `Card` (`primeng/card`)                                    | welcome-page, articles-page                                                                                                                                                                                                       |
| `PasswordDirective` (`primeng/password`)                   | login-page                                                                                                                                                                                                                        |
| `Textarea` (`primeng/textarea`)                            | articles-page                                                                                                                                                                                                                     |
| `providePrimeNG()` (`primeng/config`)                      | app.config.ts                                                                                                                                                                                                                     |
| `definePreset`/`Aura` (`@primeuix/themes`)                 | core/prime-theme.ts                                                                                                                                                                                                               |
| `primeicons.css`                                           | styles.css (`@import 'primeicons/primeicons.css';`)                                                                                                                                                                               |

`pi-*` icon classes actually in use (nav-menu, theme-toggle,
logout-button, language-switcher): `pi-th-large`, `pi-book`,
`pi-comments`, `pi-users`, `pi-plus`, `pi-arrow-right-arrow-left`,
`pi-sign-out`, `pi-sun`, `pi-moon` — 9 distinct icons, confirming
tree-shaken Lucide imports (not the full set) is feasible and cheap.

No other files (checked `app.ts`, `core/tour.service.ts`,
`core/root-redirect-placeholder.component.ts`,
`layout/app-shell.component.ts`, `shared/brand-wordmark.component.ts`,
`shared/no-access-state.component.ts`, `shared/tour-overlay.component.ts`,
`features/dashboard/dashboard-page.component.ts`,
`features/dashboard/members-breakdown-card.component.ts`,
`features/members/member-detail-panel.component.ts`) import PrimeNG —
earlier substring greps for `p-` matched unrelated identifiers, not
PrimeNG usage. Scope is confirmed at exactly the 20 files listed above.

## Architectural decisions

- **Buttons → a hand-rolled `<button>` + shared Tailwind class
  constants, not a new `ButtonComponent` wrapper.** _Why:_ every current
  usage of `pButton`/`ButtonDirective` is a plain native `<button>` with
  a `severity` variant (`secondary`, `danger`) and occasionally `text`
  (ghost) styling — a thin CSS-class helper (e.g. exported
  `BUTTON_CLASSES` map or a small `buttonClass(variant, ghost)` pure
  function in `shared/button-classes.ts`) reproduces this without
  introducing component indirection for something that's just Tailwind
  classes on a native element. A full `ButtonComponent` would be
  speculative abstraction the constitution's "no speculative
  abstractions" rule already forbids, given there's no host-binding or
  behavior PrimeNG's directive gave us beyond styling.

- **Menus (nav-menu, help-menu) → native `<ul>`/`<button>` +
  Tailwind, no menu directive.** _Why:_ both current usages set
  `[popup]="false"` (nav-menu, an always-open sidebar list) or a
  manually-toggled `open()` signal (help-menu) — neither ever actually
  used PrimeNG's popup/keyboard-navigation behavior. Replacing `p-menu`
  with a plain `@for` over the existing `NavMenuItem`/`HelpMenuItem`
  arrays, rendered as native `<a>`/`<button>` elements with the same
  Tailwind classes already defined (`linkClass`, `iconClass`,
  `categoryLabelClass`), is a direct, no-behavior-change port. The
  `MenuItem` interface import from `primeng/api` is replaced by a
  small locally-defined interface (`NavMenuItem`/`HelpMenuItem` already
  extend it only for `label`/`icon`/`command`/`routerLink` fields,
  which are trivially self-declared).

- **Dropdowns/single-select lists (select-tenant Listbox,
  conversations-page Listbox) → native `<ul>`/`<li>` with
  `role="listbox"`/`role="option"` and `aria-selected`, click handlers
  already present on the underlying data.** _Why:_ grepping both usages
  shows simple single-select lists over an array with a selection
  signal — no virtualization, no filtering beyond what conversations-page
  already does itself. A hand-rolled list preserves accessibility via
  explicit ARIA roles instead of relying on the library.

- **Forms/inputs (login, tenant-create, articles, members-page
  `pInputText`; login's `pPassword`; articles' `pTextarea`) → native
  `<input>`/`<textarea>` with Tailwind focus/border/dark-mode utility
  classes matching the existing "Ink and Signal" tokens.** _Why:_
  `InputText`/`PasswordDirective`/`Textarea` are attribute directives
  applying only CSS classes and (for password) a show/hide toggle
  behavior nothing in the codebase currently exercises (`type="password"`
  is set directly in the template already) — removing them is a
  class-list substitution, not a behavior change.

- **Toast/error display (`error-state.component.ts`'s `Message`) →
  native `<div role="alert">` + Tailwind, matching the existing
  `error-state`/`no-access-state` pattern already used elsewhere in the
  codebase for non-PrimeNG error surfaces.** _Why:_ `Message` here is
  used as a static inline banner (not an actual overlay/toast service),
  so a styled `div` is a direct equivalent with no functional loss.

- **Tables (top-articles-table, members-page) → native `<table>` +
  Tailwind, with the existing global-filter/search logic re-implemented
  as a local computed signal instead of `Table#filterGlobal()`.**
  _Why:_ grepping both usages shows no sorting, pagination, or
  multi-column filtering — `p-table` is used purely for row rendering
  plus a single `globalFilterFields` search box. A `computed(() =>
rows().filter(...))` signal reproduces this exactly, consistent with
  this codebase's signal-based state convention, and removes the
  `@ViewChild('dt')` imperative-API dependency entirely (a strict
  improvement: it was the only non-signal, non-reactive state access in
  either component).

- **Cards (welcome-page, articles-page) → a `<div>` + Tailwind
  utility classes matching `metric-tile`'s/dashboard cards' existing
  non-PrimeNG card styling** (rounded-2xl border, shadow, dark-mode
  variants) already used throughout the dashboard feature even _while_
  PrimeNG was adopted (per the file list, dashboard's own card-like
  containers were never migrated to `p-card` in the first place) — this
  is the smallest-diff, most consistent choice.

- **Charts (`UIChart` in conversations-activity-chart,
  message-split-chart, metric-tile) → direct Chart.js, no
  wrapper library (not `ng2-charts`, not a new PrimeNG-equivalent).**
  _Why:_ `chart.js@^4.5.1` is **already a direct dependency**
  (`package.json`) — PrimeNG's `Chart` component was itself a thin
  wrapper around it, so removing PrimeNG doesn't require removing or
  replacing Chart.js, only the wrapper. `DECISIONS.md`'s PrimeNG-adoption
  entry chose PrimeNG's own `Chart`/`Table` specifically _to avoid a
  second charting dependency_ against a since-superseded `ngx-charts`
  SPEC assumption; that constraint doesn't apply here since Chart.js is
  already installed and directly instantiating it removes a dependency
  rather than adding one. Usage is trivial (one `bar` chart of daily
  counts, one `doughnut` of a 2-category split, no PrimeNG-specific
  chart features used beyond `type`/`data`/`height`) — this does not
  justify pulling in `ng2-charts` (a new Tier 3 dependency) for what a
  ~30-line standalone `ChartCanvasComponent` wrapping `new Chart(ctx,
config)` in an `effect()` (create/update/destroy tied to the
  component lifecycle, consistent with this codebase's signal-based
  state pattern) already covers. This new `shared/chart-canvas.component.ts`
  is the one new shared component introduced by this removal, and is
  intentionally generic over `type`/`data`/`height` inputs so both chart
  usages (and `metric-tile`, previously undocumented in the initial
  scope list but confirmed via grep to also import `UIChart`) share it
  instead of each hand-rolling `new Chart()` separately.

- **`period-filter`'s `SelectButton` → a native `<div role="group">`
  of toggle `<button>`s**, reusing the button-classes helper above with
  an `aria-pressed` state per option. _Why:_ `SelectButton` here is a
  single-select segmented control over a small fixed `Period` enum
  (`7d`/`30d`/`90d`/`all`) — no different from a manually-rendered
  button group with `aria-pressed`/active-state Tailwind classes.

- **Icons → `lucide-angular`, importing only the 9 icons actually
  used** (`LayoutGrid`, `BookOpen`, `MessagesSquare`, `Users`, `Plus`,
  `ArrowRightLeft`, `LogOut`, `Sun`, `Moon` — Lucide's nearest
  equivalents to the 9 confirmed `pi-*` classes above) via
  `LucideAngularModule.pick({ IconName, ... })` in `app.config.ts`,
  rather than importing the full icon set. _Why:_ this is
  `lucide-angular`'s documented tree-shaking mechanism and matches the
  constitution's "no speculative abstractions" / dependency-hygiene
  spirit — pulling every Lucide icon in when 9 are used would silently
  bloat the bundle for no benefit.

- **`core/prime-theme.ts` is deleted outright, not ported.** _Why:_
  it existed solely to map "Ink and Signal" Tailwind tokens into a
  PrimeNG `definePreset`; with no PrimeNG components left to theme,
  the mapping has no consumer. The underlying `ink-*`/`signal-*`
  Tailwind tokens in `styles.css` are untouched — they're the actual
  brand definition, not a PrimeNG artifact, and every replacement
  pattern above continues to consume them directly via Tailwind classes.

- **`app.config.ts`'s `providePrimeNG()` call and its import are
  removed in the same task as the last component consumer**, not
  earlier — a dangling provider for an already-removed module would be
  dead code, but removing it before all components stop injecting
  PrimeNG's `MessageService`/`Menu`-internal DI tokens would break the
  app at runtime. See task ordering below.

## Components and routes

No route changes. Every affected component keeps its existing selector,
inputs/outputs, and template structure (data-testid attributes are
preserved verbatim — they're load-bearing for existing Vitest specs).
New file: `shared/chart-canvas.component.ts` (+ `.spec.ts`), consumed by
`conversations-activity-chart`, `message-split-chart`, `metric-tile`.
New file: `shared/button-classes.ts` (pure functions/constants, no
component), consumed by every button-migration task below.

## Consumed API contracts

None — this is a pure presentation-layer refactor. No backend endpoint
contracts change.

## State and data

No new shared state/service. `top-articles-table`'s and
`members-page`'s ad-hoc search boxes each gain a local `computed()`
filter signal (component-local state, not promoted to a service — matches
existing scope of the state they filter).

## Dependencies

- **Remove:** `primeng`, `@primeuix/themes`, `primeicons` (`package.json`
  `dependencies`). `@angular/cdk` was pulled in solely as PrimeNG's peer
  dependency — grep confirms no direct `@angular/cdk` import anywhere in
  `src/app`, so it is removed too; if that grep is wrong for any reason,
  flag it before removing rather than silently keeping/dropping it.
- **Add:** `lucide-angular` (Tier 3, but pre-confirmed by the app owner
  in the `DECISIONS.md` entry this PLAN implements — not a fresh ask).
- **No other new dependency.** Chart.js is already present; no
  `ng2-charts`, no new table/menu/listbox library.

## Testing strategy

Every migrated component keeps its existing Vitest spec passing
unchanged wherever the spec asserts on `data-testid`/rendered text
(the majority) — those are the regression guard. Specs that currently
assert on a PrimeNG-specific DOM structure or class (if any — verified
per-component during the task, not assumed here) are updated in the same
task as the component, following TDAD: adjust the assertion to the new
DOM shape first (Red), then confirm the migrated component satisfies it
(Green). `shared/chart-canvas.component.ts` gets a new spec asserting it
constructs/destroys a `Chart` instance keyed on its `type`/`data`
inputs (mocking `chart.js`'s `Chart` constructor, consistent with how
`conversations-activity-chart.component.spec.ts`/
`message-split-chart.component.spec.ts` presumably already mock
`UIChart` today — confirm and reuse that mocking pattern rather than
inventing a new one). `shared/button-classes.ts` gets a small unit spec
covering each variant/ghost combination in use.

## Deviations from this PLAN during implementation

Recorded per TASKS.md task 26. Neither changes the architectural
decisions above — both are implementation-detail corrections discovered
while executing the tasks.

- **Icon library package name and wiring.** `lucide-angular` (the
  package named above) is deprecated upstream in favor of
  `@lucide/angular`, and neither has an Angular 22-compatible peer
  range except `@lucide/angular` — so `@lucide/angular` was installed
  instead. More importantly, `@lucide/angular`'s API is not
  `LucideAngularModule.pick({...})` + `<lucide-icon name="...">`: each
  icon is its own standalone component with an attribute selector
  (e.g. `LucideSun` → `<svg lucideSun>`), imported directly by the
  components that use it. This is tree-shaken by construction (only
  imported icons end up in the bundle) with no central provider/module
  wiring in `app.config.ts` at all — a stronger version of the same
  tree-shaking goal the PLAN described, just via per-component imports
  instead of a `.pick()` allow-list.

- **`shared/chart-canvas.component.ts`'s Chart.js seam is dependency-
  injected, not `vi.mock`-based.** `conversations-activity-chart.component.spec.ts`/
  `message-split-chart.component.spec.ts` turned out not to mock
  `UIChart`/`chart.js` at all (they only assert on rendered markup and
  tolerate the harmless jsdom "canvas not implemented" console warning),
  so there was no existing mocking pattern to reuse. The new
  `chart-canvas.component.spec.ts` initially used `vi.mock('chart.js')`,
  which passed in isolation but failed 3/4 tests only when run as part
  of the full `npm test` suite — Angular's `@angular/build:unit-test`
  builder bundles every spec file together, so `chart.js` ends up a
  shared module instance across spec files rather than one scoped per
  file, and the mock didn't reliably apply in that shared context. Fixed
  by injecting the `Chart` constructor via a `CHART_CTOR` `InjectionToken`
  (defaulting to the real `Chart` class, overridden via
  `TestBed`/`useValue` in the spec) — deterministic regardless of module
  bundling/ordering, and arguably a better pattern for this codebase's
  DI-first conventions anyway.
