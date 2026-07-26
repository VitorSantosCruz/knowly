# TASKS — primeng-migration

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> This first batch covers the architecture/setup phase only (this
> PLAN's scope) — screen-by-screen migration tasks belong to follow-up
> TASKS.md batches per item in PLAN.md's "Migration order," written when
> that work is picked up.

- [x] 1. Add `primeng@22.0.0`, `@primeuix/themes@3.0.0`,
      `primeicons@8.0.0`, `@angular/cdk@22.0.0` to `package.json`.
- [x] 2. Create `src/app/core/prime-theme.ts` with the `InkSignalPreset`
      (`definePreset(Aura, {...})`) mapping `ink-*`/`signal-*` onto
      PrimeNG's `surface`/`primary` semantic tokens for both
      `colorScheme.light` and `colorScheme.dark`.
- [x] 3. Wire `providePrimeNG({ theme: { preset: InkSignalPreset,
      options: { darkModeSelector: '.dark' } } })` into `app.config.ts`.
- [x] 4. Import `primeicons/primeicons.css` in `styles.css`.
- [x] 5. Migrate `theme-toggle.component.ts` to PrimeNG's `[pButton]`
      directive with a `pi pi-sun`/`pi pi-moon` icon, as the proof of
      concept (REQ-1/REQ-2/REQ-3/REQ-4).
- [x] 6. Raise `angular.json`'s production `initial` budget
      `maximumWarning` from 500kB to 750kB to account for the added
      library.
- [x] 7. Update `knowly-app/CLAUDE.md`'s conventions section to name
      PrimeNG as the component library.
- [x] 8. Write the `DECISIONS.md` entry recording the migration
      decision and its "applies to new decisions" guidance.
- [x] 9. Run `npm run format:check && npm test && npm run build` and
      confirm everything is green.
- [x] 10. Update `PROJECT_STATUS.md`'s frontend feature table and "Next
      up" section to reflect this phase's completion and the open
      follow-up work.

## Batch 2 — chrome + shared buttons + menus (2026-07-25)

> Covers PLAN.md's migration-order items 1-3. Items 4-7 (forms, cards,
> tables, feature-specific screens) remain open follow-up work.

- [x] 11. Migrate `nav-menu.component.ts` to PrimeNG `p-menu` (inline,
      `[popup]="false"`, one per permission-gated category), with custom
      `#submenuheader`/`#item` templates preserving every existing
      `data-testid`/`data-tour-id`/`routerLink`/permission gate.
- [x] 12. Replace `nav-menu.component.ts`'s inline SVG icons with
      PrimeIcons (`pi-th-large`, `pi-book`, `pi-comments`, `pi-users`,
      `pi-plus`, `pi-arrow-right-arrow-left`).
- [x] 13. Scope the permanently-dark sidebar/header chrome
      (`app-shell.component.ts`) so PrimeNG components inside it always
      render in the dark palette regardless of `ThemeService`'s
      app-wide toggle — resolved via a static `class="dark"` on the
      `<aside>`/`<header>` themselves (see PLAN.md's "Chrome dark-mode
      scoping" section), not a new mechanism.
- [x] 14. Migrate `logout-button.component.ts` and
      `language-switcher.component.ts` to `[pButton]`
      (`text severity="secondary"`), following `theme-toggle`'s pattern;
      `logout-button` additionally swaps its inline SVG for `pi-sign-out`.
- [x] 15. Migrate `help-menu.component.ts`'s dropdown to a `p-menu`
      (inline, driven by the component's own `open` signal, not
      PrimeNG's popup visibility) with a custom `#item` template for its
      `restart-tour` action, preserving `data-testid`/`aria-expanded`.
- [x] 16. Raise `angular.json`'s production budget `maximumWarning` from
      750kB to 800kB (bundle grew to 752.65kB after adding `Menu`/
      `ButtonDirective` across five components).
- [x] 17. Run `npm run format`, then
      `npm run format:check && npm test && npm run build` — all green,
      no test changes needed (existing specs query by `data-testid`/
      text content, not structure).
- [x] 18. Update this file, `PLAN.md`'s migration order, and
      `PROJECT_STATUS.md` to reflect batch 2's completion and the
      still-open follow-up work (forms, cards, tables, feature screens;
      `error-state`/`no-access-state`/`tour-overlay` deliberately
      deferred — see PLAN.md note).
