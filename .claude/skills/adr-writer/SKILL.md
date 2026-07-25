---
name: adr-writer
description: Use when writing a PLAN.md that involves a technical decision with no exact existing precedent in DECISIONS.md, or when explicitly asked to document an architectural/code-level decision. Triggers on phrases like "documenta essa decisão", "por que escolhemos", or any PLAN.md section describing a novel tradeoff.
---

# adr-writer

Writes a new entry in `DECISIONS.md` (backend) in the exact format
already established there, and applies the Tier 1/2/3 self-check from
that file before writing anything.

## Rules & anti-patterns

- **DO** classify the decision first, out loud, before writing:
  - Tier 1 (just do it): implementation detail within an approved
    SPEC/PLAN, following an existing precedent.
  - Tier 2 (decide, but document): a technical choice with no exact
    precedent — pick one, write the entry, proceed.
  - Tier 3 (always ask first): touches `VISION.md`'s undecided list,
    any product/business decision, a security/privacy tradeoff with no
    established pattern, a new external dependency, or anything hard to
    reverse (data-lossy schema change, deletion, another tenant's
    isolation guarantee). **If unsure which tier, treat it as Tier 3.**
- **STRICTLY PROHIBITED**: writing a Tier 3 decision into `DECISIONS.md`
  as if it were already approved. A Tier 3 entry only gets written
  *after* the user has explicitly decided — this skill drafts the
  question, not the answer, for those.
- **DO** find the closest existing entry first and ask whether the same
  underlying principle already applies (e.g. "isolation must never be
  bypassed," "don't let startup depend on an external API," "verify a
  syntax actually does what it looks like it does") before reasoning
  from scratch — most "novel" decisions on this project turn out to be
  an existing principle applied to a new surface.
- **DO** write the entry so a *future* reader with zero conversation
  history can reconstruct the reasoning — not just the conclusion.

## Execution steps

1. Read `DECISIONS.md`'s "Decision-making authority" section in full —
   it is not boilerplate, it's the actual tier-classification rule.
2. Search `DECISIONS.md`'s existing entries for the closest analog.
3. If Tier 1 or Tier 2: write the entry (template below), append it
   under "Architectural decisions (with rationale)", in the same
   what/why/applies-to-new-decisions shape as existing entries.
4. If Tier 3: do not write the entry yet. Surface the tradeoff to the
   user as a direct question (not buried in prose), and only write the
   entry once they've answered — dated, with their decision stated
   plainly, same as the "Cross-repo SPEC placement (2026-07-25)" or
   "`${VAR:?message}`" entries already in the file.
5. Cross-check: does this decision require an amendment to an existing
   SPEC's "Out of scope"? If so, that's a *separate*, Tier 3 action
   (`user-story-ears-writer` skill), not something this skill silently
   folds in.

## Template

```markdown
### <Short, specific title — the decision itself, not the topic area>

<What was decided — one or two sentences, concrete, not vague>.
**Why:** <the actual reasoning — a real incident, a measured tradeoff,
a security/product principle from VISION.md/DECISIONS.md this extends>.
**Applies to new decisions:** <how a future, similar decision should
reason from this one — what to check, what NOT to do without
re-validating>.
```

Real example of the shape (already in this file — use as the style
reference, don't literally copy the content):

```markdown
### `${VAR:?message}` is NOT a real Spring "required property" syntax

Discovered 2026-07-25 from a real, reproduced bug: ... **Fix applied**:
every one of those properties now uses bare `${SOME_ENV_VAR}` ...
**Applies to new decisions:** never assume a placeholder/templating
syntax works the same across tools just because the tokens look similar...
```
