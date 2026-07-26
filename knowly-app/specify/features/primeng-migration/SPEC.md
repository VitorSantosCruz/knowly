# SPEC — primeng-migration

> The what and the why. No technical implementation details.
>
> This is an infrastructure/tooling SPEC, not new user-facing behavior:
> it exists because a SPEC is required before a PLAN per `constitution.md`,
> even though the "requirement" here is an explicit, already-made product
> decision from the app owner rather than a new user story.

## Context and motivation

Several rounds of hand-rolled Tailwind components (buttons, menus, cards,
forms) have left the frontend looking inconsistent and amateurish. The
owner has decided, explicitly and out of scope for this document to
re-litigate, to replace all hand-built interactive components with
**PrimeNG** (+ PrimeIcons) as a real component library — a full
migration, not partial adoption — while keeping the existing "Ink &
Signal" brand identity (colors, fonts, motion tokens already defined in
`styles.css`) and the existing light/dark toggle.

## User stories

- As a developer building any new screen, I want a consistent,
  pre-built component library available so that I don't hand-roll
  buttons/menus/tables/forms from scratch.
- As a user, I want the app's visual language (colors, light/dark mode)
  to look and behave the same after the migration, so the change is
  invisible to me except for a more polished, consistent UI.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The frontend shall use PrimeNG components for
   interactive UI elements (buttons, menus, cards, forms, tables) instead
   of hand-rolled Tailwind markup.
2. **[Ubiquitous]** The frontend shall use PrimeIcons for iconography
   instead of inline SVGs.
3. **[Ubiquitous]** PrimeNG's theme shall render using the existing
   "Ink & Signal" brand colors (`ink-*`/`signal-*`), not PrimeNG's
   default palette.
4. **[State-Driven]** While the app's light/dark toggle (`ThemeService`,
   `.dark` class on `<html>`) is in either state, PrimeNG components
   shall render in the matching light/dark variant, driven by the same
   `.dark` class — no second, independent dark-mode toggle.
5. **[Ubiquitous]** Tailwind CSS shall remain in use for page layout,
   spacing, and utility classes; PrimeNG is additive for interactive
   components, not a wholesale CSS framework replacement.

## Non-functional requirements

- Accessibility: PrimeNG components' built-in ARIA/keyboard support is
  preserved (no reason to strip it); no regression versus current
  hand-rolled components.
- Performance: production bundle budget adjusted to account for the
  added library (`angular.json`, `maximumWarning` raised from 500kB to
  750kB — see PLAN for why).
- Responsiveness: unchanged from current breakpoints/behavior.

## Acceptance criteria

- [ ] `primeng`, `@primeuix/themes`, and `primeicons` are added to
      `package.json` and wired into `app.config.ts`.
- [ ] A custom PrimeNG preset exists that maps `ink-*`/`signal-*` tokens
      onto PrimeNG's semantic design tokens for both light and dark mode.
- [ ] At least one component is migrated end-to-end (proof of concept)
      demonstrating the theme renders correctly in both modes.
- [ ] `knowly-app/CLAUDE.md` reflects PrimeNG as the component-library
      convention.
- [ ] `DECISIONS.md` records the decision and its "applies to new
      decisions" guidance.
- [ ] `npm run format:check && npm test && npm run build` all pass.

## Out of scope

- The actual screen-by-screen migration of every existing component
  (sidebar, header, buttons, cards, menus, forms, tables across all
  feature screens) — that is follow-up implementation work, tracked in
  this feature's PLAN as a priority order, not built here.
- Any change to `dashboard-analytics`'s not-yet-approved SPEC (its
  ngx-charts assumption is superseded by PrimeNG's `Chart`/`Table`
  components, but that SPEC is revised separately, not as part of this
  migration).
- Any decision about whether the sidebar/header "chrome" should stop
  being permanently dark — this SPEC only requires that PrimeNG's theme
  integrate with whatever the chrome ends up being; see PLAN for the
  judgment call made on today's already-existing permanently-dark
  chrome specifically.
