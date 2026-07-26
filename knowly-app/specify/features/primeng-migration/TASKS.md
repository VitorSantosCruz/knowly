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
