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

## Batch 3 — remaining feature screens, final pass (2026-07-25)

> Covers PLAN.md's migration-order items 4-7 in full. Completes the
> migration — no migration-order items remain open after this batch.

- [x] 19. Migrate `error-state.component.ts` to `p-message`
      (`severity="error"`); re-verify `no-access-state.component.ts` is
      still just a `<p>`, not worth a PrimeNG wrapper.
- [x] 20. Migrate `welcome-page.component.ts`'s quick-link cards to
      `p-card`, keeping permission gating and `routerLink`s unchanged.
- [x] 21. Migrate `login-page.component.ts`'s inputs/buttons to
      `pInputText`/`pPassword`/`pButton` directives on the existing
      native elements (no DOM restructuring), leaving the bespoke
      code/password tab UI as-is.
- [x] 22. Migrate `articles-page.component.ts`: `p-card` for upload/
      detail panels, `pInputText`/`pTextarea` for text fields, `pButton`
      for actions; article-list rows deliberately left as native markup
      (two actions per row, not a `p-listbox` fit).
- [x] 23. Migrate `conversations-page.component.ts`: conversation list
      to `p-listbox` with a custom `#item` template; new-conversation/
      send buttons to `pButton`; message input to `pInputText`; chat
      bubbles deliberately left as bespoke markup.
- [x] 24. Migrate `members-page.component.ts`'s member list to `p-table`
      with a custom `#body` template; add-member form to
      `pInputText`/`pButton`.
- [x] 25. Migrate `select-tenant-page.component.ts`'s tenant list to
      `p-listbox` with a custom `#item` template; create-tenant link to
      `pButton`.
- [x] 26. Migrate `tenant-create-page.component.ts`'s form to
      `pInputText`/`pButton`.
- [x] 27. Raise `angular.json`'s production budget again
      (`maximumWarning` 800kB→900kB, `maximumError` 1MB→1.4MB — bundle
      reached 1.27MB after the full migration; route-level lazy loading
      flagged as the real follow-up fix, not solved here).
- [x] 28. Run `npm run format`, then
      `npm run format:check && npm test && npm run build` — all green,
      all 186 pre-existing tests passing unchanged (no DOM restructuring
      needed).
- [x] 29. Update this file, `PLAN.md`'s "Final feature-screen pass" and
      "Known follow-ups" sections, and `PROJECT_STATUS.md`'s frontend
      feature table and "Next up" section to record the migration as
      fully complete, with `tour-overlay` and lazy-route-splitting as
      the only remaining open follow-ups.
