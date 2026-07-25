---
name: user-story-ears-writer
description: Use whenever a request describes a feature, bug-that's-actually-a-behavior-change, or business need with no approved SPEC.md yet, or when an existing SPEC.md needs a new requirement added. Triggers on phrases like "preciso de", "quero que", "adiciona a funcionalidade de", or any ask with no linked specify/features/<name>/SPEC.md.
---

# user-story-ears-writer

Writes (or amends) a `SPEC.md` using EARS/GEARS syntax, per
`specify/memory/constitution.md` and `specify/templates/spec-template.md`.
Never write implementation code from this skill — its only output is a
SPEC.md and, when needed, a set of blocking clarifying questions.

## Rules & anti-patterns

- **DO** phrase every requirement as exactly one of: Ubiquitous,
  Event-Driven, State-Driven, Optional Feature, Unwanted Behavior,
  Complex. A requirement you can't fit one of these six shapes is not
  specific enough — keep asking.
- **DO** write an explicit "Out of scope" section. An unlisted concern
  is not "handled," it's undecided — say so.
- **DO** check `VISION.md`'s "What's deliberately not decided yet" and
  `DECISIONS.md`'s Tier 3 list before drafting anything that touches
  product direction, billing, self-service signup, security tradeoffs,
  or a new external dependency — those need the user's explicit decision
  captured as an answered question in the SPEC, not your own guess.
- **STRICTLY PROHIBITED**: editing an existing SPEC's "Out of scope"
  line to quietly remove a boundary and then proceeding to implement
  it. This exact failure happened once on this project (an AI removed
  "Logout not addressed here" and shipped logout in the same breath) —
  it is the canonical violation this skill exists to prevent. If a task
  reveals the SPEC needs to grow, stop and ask; don't self-approve.
- **STRICTLY PROHIBITED**: writing PLAN.md-level detail (tech stack,
  schema, package names) inside SPEC.md. SPEC.md is implementation
  -agnostic by design — that's what lets PLAN.md (or the whole stack)
  change later without re-litigating whether the requirement is correct.
- **DO** determine cross-repo placement before writing: backend
  behavior's SPEC lives in `knowly/specify/features/<name>/`, frontend
  behavior's SPEC lives in `knowly-app/specify/features/<name>/`. A
  feature spanning both gets two SPECs that cross-reference each
  other's contract, never one shared document in either repo.

## Execution steps

1. Read `VISION.md`, `PROJECT_STATUS.md` (both repos' if ambiguous which
   repo owns this), and `DECISIONS.md` before drafting anything.
2. If a `specify/features/<name>/SPEC.md` already exists for this area,
   read it fully — you may be amending it, not creating a new one.
3. Identify every ambiguity a human PO would ask about: who triggers
   this, what's the success/failure path, what data is involved, does
   it touch anything from `DECISIONS.md`'s Tier 3 list. Ask all of them
   together via one clarifying-question pass — don't trickle out
   questions one at a time across multiple turns (approval fatigue is a
   named anti-pattern, see `sdd-methodology.md`).
4. Draft `SPEC.md` from `specify/templates/spec-template.md`:
   Context and motivation → User stories → Requirements (EARS/GEARS,
   numbered REQ-N) → Non-functional requirements → Acceptance criteria
   (checkbox list, one per testable behavior) → Out of scope.
5. If amending an existing SPEC's scope: never edit silently. Show the
   user the exact diff of what's changing and why, get explicit
   confirmation, and note the amendment date inline (see any recent
   `SPEC.md`'s "(Amended YYYY-MM-DD — ...)" convention for the format).
6. Present the draft for approval before any PLAN.md work starts. Do
   not proceed past this point without an explicit go-ahead.

## Templates

EARS pattern reference (exactly one per requirement):

```
- **REQ-N [Ubiquitous]** The <system> shall <action/property>.
- **REQ-N [Event-Driven]** When <trigger>, the <system> shall <action>.
- **REQ-N [State-Driven]** While <state>, the <system> shall <action>.
- **REQ-N [Optional Feature]** Where <config>, the <system> shall <action>.
- **REQ-N [Unwanted Behavior]** If <error condition>, then the <system>
  shall <action>.
- **REQ-N [Complex]** Where <config>, while <state>, when <trigger>, the
  <system> shall <action>.
```

Amendment marker (when changing an already-approved requirement):

```
- **REQ-5 [State-Driven]** *(Amended YYYY-MM-DD — reverses the original
  version of this requirement, which <old behavior>.)* While <new state>,
  the <system> shall <new action>.
```

Open-questions block (when a decision is genuinely the user's to make):

```
## Open questions — need your decision before PLAN.md

1. **<short label>**: <the actual tradeoff>. I'd lean <option> because
   <reasoning> — flagging since <why it's the user's call, not yours>.
```
