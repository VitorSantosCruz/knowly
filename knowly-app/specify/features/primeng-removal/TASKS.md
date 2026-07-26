# TASKS — primeng-removal

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

## Ordering rationale

Shared building blocks (`button-classes.ts`, `lucide-angular` icon
wiring, `chart-canvas.component.ts`) are built **first**, before any
consumer migration, because nearly every later task depends on at least
one of them — building them last would mean either duplicating
ad-hoc styling per component or churning already-migrated components
again once the shared helper exists. Per-component migrations are then
ordered leaf-first (components with no children that also import
PrimeNG) up to composite pages, so no task ever touches a component
whose child hasn't been migrated yet. `providePrimeNG()`/`prime-theme.ts`/
`primeicons.css` wiring in `app.config.ts`/`styles.css` is removed
**only after every consumer is migrated** (removing it earlier breaks
the app at runtime for every not-yet-migrated component, per PLAN.md).
The npm dependency swap (`uninstall primeng ... && install
lucide-angular`) is last, not first, so the working tree never has a
window where components reference a package no longer in
`node_modules` — that would make `npm test`/`npm run build` fail on
every intermediate commit, defeating the "commit each completed task"
rule.

- [x] 1. Add `lucide-angular` to `package.json` and wire
      `LucideAngularModule.pick({...})` with the 9 confirmed icons
      (`LayoutGrid`, `BookOpen`, `MessagesSquare`, `Users`, `Plus`,
      `ArrowRightLeft`, `LogOut`, `Sun`, `Moon`) into `app.config.ts`'s
      providers, alongside (not yet replacing) `providePrimeNG()`. Write
      a minimal spec asserting the app still bootstraps. (This is
      additive scaffolding, not a Red/Green pair — no behavior to test
      yet.)
- [x] 2. Write `shared/button-classes.ts` unit spec (Red) covering
      default/`secondary`/`danger` severities and the `text` (ghost)
      variant, then implement the pure `buttonClass(variant, ghost)`
      helper / exported class constants (Green).
- [x] 3. Write `shared/chart-canvas.component.ts` spec (Red) asserting
      it constructs a `Chart` (mocked) with the given `type`/`data` on
      init and destroys it on input change/destroy, reusing whatever
      `Chart.js` mocking pattern already exists in
      `conversations-activity-chart.component.spec.ts`/
      `message-split-chart.component.spec.ts` (confirm the pattern
      first) — then implement the component (Green).
- [x] 4. Migrate `shared/theme-toggle.component.ts`: replace
      `ButtonDirective` with a native button + `button-classes.ts`,
      replace `pi-sun`/`pi-moon` with `<lucide-icon name="sun"/"moon">`.
      Update its spec if it asserted on PrimeNG-specific
      classes/attributes; otherwise confirm it's still green.
- [x] 5. Migrate `shared/logout-button.component.ts` the same way
      (`ButtonDirective` → native button, `pi-sign-out` → Lucide
      `log-out`).
- [x] 6. Migrate `shared/language-switcher.component.ts`
      (`ButtonDirective` → native button; confirm no icon usage beyond
      what's already covered).
- [x] 7. Migrate `shared/error-state.component.ts`: replace `Message`
      with a native `<div role="alert">` + Tailwind, matching
      `no-access-state.component.ts`'s existing non-PrimeNG pattern.
- [x] 8. Migrate `shared/help-menu.component.ts`: replace
      `ButtonDirective` (toggle button) and `Menu`/`MenuItem` with a
      native `<ul role="menu">`/`<button role="menuitem">` list driven
      by the existing `items()` computed signal; drop the `MenuItem`
      import from `primeng/api` in favor of a self-declared
      `HelpMenuItem` interface.
- [x] 9. Migrate `layout/nav-menu.component.ts`: replace `Menu`/
      `MenuItem` the same way as task 8 (native `<ul>`/`<a>` per group),
      replace the 6 `pi-*` icon classes (`pi-th-large`, `pi-book`,
      `pi-comments`, `pi-users`, `pi-plus`,
      `pi-arrow-right-arrow-left`) with the corresponding Lucide icon
      names, preserving every `data-testid`/`data-tour-id` attribute
      verbatim (load-bearing for existing specs and the onboarding
      tour).
- [x] 10. Migrate `features/dashboard/period-filter.component.ts`:
      replace `SelectButton` with a native `role="group"` of toggle
      buttons using `button-classes.ts` + `aria-pressed`.
- [x] 11. Migrate `features/dashboard/export-button.component.ts`:
      replace `ButtonDirective` with a native button.
- [x] 12. Migrate `features/dashboard/conversations-activity-chart.component.ts`:
      replace `UIChart`/`p-chart` with `shared/chart-canvas.component.ts`,
      keeping `toBarData` unchanged.
- [x] 13. Migrate `features/dashboard/message-split-chart.component.ts`
      the same way, keeping `toDonutData`/`toRows` unchanged.
- [x] 14. Migrate `features/dashboard/metric-tile.component.ts`
      (confirmed via grep to also use `UIChart`, not called out in the
      original file list) the same way.
- [x] 15. Migrate `features/dashboard/top-articles-table.component.ts`:
      replace `Table`/`InputText` with a native `<table>` + `<input>`,
      and replace the `@ViewChild('dt')`/`filterGlobal()` imperative
      filtering with a local `computed()` signal over a search-term
      signal, dropping the `Table` view-child reference entirely.
- [x] 16. Migrate `features/welcome/welcome-page.component.ts`: replace
      `Card` with a `<div>` + Tailwind card classes matching the
      dashboard's existing non-PrimeNG card styling.
- [x] 17. Migrate `features/members/members-page.component.ts`:
      replace `ButtonDirective`/`InputText`/`Table` the same way as
      tasks 11/15 (native button, native input, native table + local
      filter signal if applicable — confirm whether this table needs
      filtering; grep shows it doesn't, so plain `<table>` rows
      suffice here).
- [x] 18. Migrate `features/select-tenant/select-tenant-page.component.ts`:
      replace `ButtonDirective` (native button) and `Listbox` (native
      `<ul role="listbox">`/`<li role="option">` with
      `aria-selected`).
- [x] 19. Migrate `features/conversations/conversations-page.component.ts`
      the same way as task 18 (`ButtonDirective`, `InputText`,
      `Listbox`).
- [x] 20. Migrate `features/tenant-create/tenant-create-page.component.ts`:
      replace `ButtonDirective`/`InputText` with native equivalents.
- [x] 21. Migrate `features/login/login-page.component.ts`: replace
      `ButtonDirective`/`InputText`/`PasswordDirective` with native
      equivalents (native `<input type="password">` already implied by
      existing template attributes — confirm no show/hide toggle
      behavior is lost, per PLAN.md's assessment that none is
      currently exercised).
- [x] 22. Migrate `features/articles/articles-page.component.ts`:
      replace `ButtonDirective`/`Card`/`InputText`/`Textarea` with
      native equivalents.
- [x] 23. Remove `providePrimeNG()` and its import from
      `app.config.ts` (now that every consumer is migrated), delete
      `core/prime-theme.ts`, and remove the
      `@import 'primeicons/primeicons.css';` line from `src/styles.css`
      (the `ink-*`/`signal-*` brand tokens themselves stay untouched).
- [x] 24. Run `npm uninstall primeng @primeuix/themes primeicons` and,
      if not already present from task 1, `npm install lucide-angular`;
      also drop `@angular/cdk` from `package.json` per PLAN.md's
      dependency section (re-confirm via grep no direct import exists
      before removing).
- [x] 25. Run `npm run format:check && npm test && npm run build` (full
      verification per constitution/CLAUDE.md) and fix any residual
      reference to `primeng`/`primeicons`/`@primeuix` surfaced by the
      build.
- [x] 26. Update `PLAN.md` if any decision changed during
      implementation (e.g. a spec turning out to assert on PrimeNG DOM
      structure in a way not anticipated here), and update
      `knowly-app/CLAUDE.md`'s "Conventions already established" bullet
      if the final pattern differs from what's documented there.
