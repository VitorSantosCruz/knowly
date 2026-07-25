---
name: design-token-audit
description: Use before or when reviewing any knowly-app component's visual treatment — new screen, restyle, or a complaint that something looks inconsistent, plain, or has no animation/transition. Triggers on "deixa mais bonito", "sem animação", "inconsistente visualmente", "redesign".
---

# design-token-audit

Checklist + token reference for **knowly-app**'s visual language
(Angular + Tailwind CSS). Produces either a pass/fail audit of an
existing component or a concrete restyle plan — never a full ad-hoc
redesign without scoping which screens first.

## Rules & anti-patterns

- **DO** use the established palette exactly: slate for neutral
  surfaces/text (`bg-slate-100`/`dark:bg-slate-950`,
  `text-slate-900`/`dark:text-white`, secondary text no lighter than
  `text-slate-500`/`dark:text-slate-400` — contrast floor), indigo for
  primary actions (`bg-indigo-600 hover:bg-indigo-500`, focus ring
  `focus:ring-indigo-500/20`).
- **STRICTLY PROHIBITED**: a new interactive element with no
  `transition` class and no `hover:`/`focus:` state. This is a
  confirmed, real user complaint on this project ("tá tudo solto, tudo
  duro, sem animação") — treat "renders and is clickable" as
  incomplete, not done, until it has a transition.
- **STRICTLY PROHIBITED**: a primary action (create/submit/confirm)
  styled as plain inline text at the end of a list or page. It gets a
  real button (`rounded-lg`, solid background, shadow), positioned near
  the content it acts on — see the `/select-tenant` "create tenant"
  button fix as the before/after reference.
- **DO** add every new user-facing string to **both**
  `public/i18n/en.json` and `public/i18n/pt-BR.json` in the same
  change — a string in only one language file is incomplete, not a
  follow-up.
- **DO** reuse an existing shared component
  (`app-help-menu`/`app-language-switcher`/`app-theme-toggle`/
  `app-nav-menu`) before building a new one that duplicates its job.
- **DO** carry accessibility forward: `role="tablist"`/`role="tab"`/
  `aria-selected` for tab-like UI, `aria-live` for dynamically-changing
  content (tour steps, async state changes), full keyboard operability
  — these are already established patterns (see the login screen's tab
  implementation), not aspirational.

## Execution steps (audit mode)

1. Open the component's template. For every clickable/focusable
   element: confirm a `transition` class exists, confirm a `hover:`
   and/or `focus:` state exists, confirm dark-mode parity
   (`dark:` variant present wherever a light-mode color is set).
2. For every user-facing string: confirm it goes through
   `{{ 'key' | transloco }}`, not hardcoded text, and confirm the key
   exists in both `en.json` and `pt-BR.json`.
3. For every primary action: confirm it's a real button/prominent link,
   not a text-only link buried at the end of content.
4. Report findings as a concrete list (file:line), not a vague "could
   be nicer" — each finding should be actionable as a one-line fix.

## Execution steps (new component / restyle mode)

1. Confirm which shared components already cover part of the need —
   reuse before building.
2. Draft the Tailwind classes using the token reference below.
3. Add i18n keys to both language files before wiring the template.
4. Hand off to `frontend-engineer`/`angular-component-builder` for the
   actual component logic — this skill owns the visual treatment, not
   the state management.

## Token reference

```
Surfaces:      bg-slate-100 dark:bg-slate-950  (page)
               bg-white dark:bg-slate-900       (card)
Text:          text-slate-900 dark:text-white   (primary)
               text-slate-500 dark:text-slate-400 (secondary — contrast floor)
Primary action: bg-indigo-600 hover:bg-indigo-500 text-white
               rounded-lg px-4 py-2 shadow-sm transition
Secondary link: text-indigo-600 hover:text-indigo-500 transition
Card:          rounded-2xl border border-slate-200 dark:border-slate-800
               shadow-lg shadow-slate-200/60 dark:shadow-none
Pill/nav link: rounded-full px-3 py-1.5 text-sm
               hover:bg-slate-200/70 dark:hover:bg-slate-800 transition
Focus ring:    focus:border-indigo-500 focus:ring-2
               focus:ring-indigo-500/20 focus:outline-none
```
