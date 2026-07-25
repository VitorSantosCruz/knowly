# Spec-Driven Development — deep-dive reasoning

> **Precedence**: this document is the authoritative source of truth on
> **SDD methodology itself** — what the process is, why each mechanic
> exists, what counts as violating it. `specify/memory/constitution.md`
> is this project's *application* of SDD to its specific stack and
> conventions (Java/Spring Boot/Angular versions, security posture,
> formatting tools, commit conventions) — that stack-specific layer
> stands on its own. But where `constitution.md`'s *process* mechanics
> (not its tech-stack rules) turn out to diverge from correct SDD
> practice as described here, **this document is what "correct" is
> measured against** — flag the drift and bring `constitution.md` into
> alignment, don't dismiss this document as the subordinate one. No
> agent or skill in this ecosystem (`.claude/agents/`, `.claude/skills/`)
> may violate the methodology described here; that is the actual
> constraint this file exists to enforce.
>
> Content synthesized from external research on Spec-Driven Development
> methodology (2026-07-25) — no external links are kept here on purpose,
> since a link can go dead while the reasoning it captured shouldn't.

## Why SDD exists at all: the actual failure mode it prevents

The alternative to SDD is what the industry calls **"vibe coding"**:
prompting an AI, eyeballing whether the result "looks right," making
ad-hoc tweaks, moving on. This works fine for a throwaway script. It is
actively dangerous for a system meant to last, because:

- **The problem was never that AI writes bad code.** It's that humans
  (and AI assistants acting without a spec) under-specify what's
  actually required, and the AI faithfully builds the *wrong, but
  plausible-looking*, thing. Every ambiguity a human would normally ask
  a clarifying question about, an unspecified AI silently guesses at
  instead — and that guess becomes invisible technical debt: code that
  looks done, passes a cursory glance, and fails in a scenario nobody
  wrote down because nobody thought to.
- **At AI-generation speed, line-by-line human review stops being a
  real safety net.** A person can carefully read 200 lines. They cannot
  carefully read the thousands an agent can produce in one turn. The
  only lever left that actually scales is raising the abstraction level
  of what gets reviewed — reviewing a SPEC's requirements and a PLAN's
  decisions, not auditing every generated line.
- **This reframes what "engineer" means on this project.** The job
  shifts from "person who writes syntax" to "person who writes precise
  intention" — deciding *what* must be true and *why*, precisely enough
  that an AI agent's job (the *how*) has no room left to silently
  improvise something wrong. This is exactly why `CLAUDE.md`/
  `constitution.md` insist a SPEC be written before any code, and why
  Tier 3 in `DECISIONS.md` exists: some decisions are precisely the kind
  of thing that must never be silently guessed.

## What a SPEC actually is (and isn't)

A SPEC is not documentation-as-afterthought. Treat it as the **durable
asset**; the code is the disposable, regenerable artifact compiled from
it for one specific technology stack at one point in time. Two concrete
implications for how you should behave:

1. **If code and SPEC disagree, that is a bug in one of them — decide
   which, explicitly, don't just let the code's behavior quietly become
   the new truth.** A SPEC nobody updates after a real behavior change
   is worse than no SPEC, because it actively lies to the next reader
   (human or AI) about what the system does. This is exactly why
   `constitution.md` says "behavior changes always update the SPEC
   first" — it is not paperwork, it is what keeps the SPEC worth trusting.
2. **A future stack migration should be "update `constitution.md`/`PLAN.md`,
   regenerate the implementation from the still-valid `SPEC.md`," not "hand
   -rewrite the code and hope the requirements survived the rewrite by
   memory."** This is the concrete payoff of keeping SPEC.md
   implementation-agnostic (constitution.md already mandates this) — it's
   not stylistic, it's what makes the SPEC portable across a future
   technology change this project hasn't made yet.

## The three-layer context every session is actually operating inside

Think of every conversation as three layers of instruction, stacked:

1. **The fixed layer** — `constitution.md` (immutable rules: stack,
   conventions, security posture) and this file. This is what makes an
   agent behave like *this project's* senior engineer instead of a
   generic one.
2. **The dynamic layer** — the specific `SPEC.md`/`PLAN.md`/`DECISIONS.md`
   entries relevant to the current feature, `PROJECT_STATUS.md`'s
   current state.
3. **The task layer** — the one `TASKS.md` item actually being executed
   right now.

The reason to read files in that order (constitution → SPEC/PLAN/status
→ the task) every session isn't ritual — it's that each layer *bounds*
what the next layer is allowed to mean. A task read without its PLAN's
context, or a PLAN read without its SPEC's "Out of scope" section, is
exactly how scope silently drifts. This is the same reasoning
`DECISIONS.md`'s real incident (an AI editing out an "Out of scope" line
and implementing it anyway) already illustrates from the opposite
direction — it happened *because* the fixed/dynamic boundary wasn't
respected.

## Builder/Verifier: why every task ends with independent verification, not self-report

Don't trust a task's own claim that it's done. The reliable pattern is
two roles, even when both are played by the same session:

- **Builder**: implements task N against `SPEC.md`'s requirement and
  `PLAN.md`'s decision.
- **Verifier**: independently checks the *result* against the SPEC's
  actual acceptance criteria and `constitution.md`'s rules — not "does
  this look reasonable" but "does the red test from before this task
  now pass, and does it test the actual requirement, not a weaker proxy
  for it."

This project already has a mechanical form of this: **TDAD (Red → Green)**
is exactly the Builder/Verifier loop, with the failing test as the
Verifier. The lesson from a real incident this session (a background
`mvnw` test run reported "exit code 0" while a real test was failing,
because the exit code was read off `tail`, the last command in a pipe,
not off `mvnw` itself) is the same principle at the tooling level:
**never accept a first-pass result as "green" without independently
confirming the actual, unmediated signal.** "The agent said it passed"
is not verification; a red test turning green *for the right reason* is.

## Hierarchical rigidity: what must never be improvised vs. what may be

Not every line of a PLAN carries equal weight. Split it explicitly:

- **Contract-level (rigid, non-negotiable within this feature):**
  requirements from SPEC.md, API shapes and status codes from PLAN.md,
  anything DECISIONS.md already established as precedent (tenant
  isolation via the Hibernate filter, CSRF-exemption boundaries,
  required-env-var semantics, etc.). An agent does not have discretion
  here — deviating is exactly the Tier 3 violation DECISIONS.md warns
  about.
- **Implementation-level (flexible):** which private helper method
  structure to use, variable names, minor refactors that don't change
  behavior — Tier 1 in `DECISIONS.md`'s own framework. This is where an
  agent's judgment is not just allowed but expected; over-specifying
  down to this level (writing what amounts to pseudocode in the PLAN)
  wastes effort and removes exactly the kind of local judgment TDAD's
  Green-state code should be free to exercise.

Getting this split wrong in either direction breaks things: **too rigid**
(spelling out every implementation line) turns SDD into a slow,
bureaucratic waterfall — a real, valid critique of the methodology when
misapplied, and exactly why this project's PLAN.md stays at the
architectural-decision level, not pseudocode. **Too loose** (a vague
SPEC with no explicit acceptance criteria) hands an agent's silent
guesswork the same failure mode "vibe coding" has — the entire opening
problem this methodology exists to solve.

## Concrete anti-patterns to actively watch for (not just avoid in theory)

- **Approval fatigue**: don't ask the user to bless a dozen tiny
  micro-decisions mid-task. Batch decisions at the natural gates this
  project already has — SPEC approval, then PLAN, then implementation —
  the same reasoning behind why `AskUserQuestion` should cluster real
  open questions at the SPEC stage rather than trickling out one at a
  time during coding.
- **API/behavior hallucination**: an agent inventing a library method,
  config key, or framework behavior that doesn't actually exist and
  proceeding as if it does. This project already had a live example
  (assuming `${VAR:?message}` was a real Spring "required property"
  syntax — it's Docker Compose/shell syntax, and Spring silently used the
  literal message string as a default instead of failing, corrupting
  real data before anyone noticed). **The mitigation is always the
  same: verify the actual behavior of the actual mechanism in play —
  a two-line standalone test, reading the actual dependency's source/
  docs, or an actual empirical run — before trusting a "should work"
  assumption, especially for anything security- or data-integrity
  -relevant.**
- **Silent scope drift**: treating "the SPEC didn't cover this" as
  license to decide the answer yourself instead of a signal to stop and
  ask. Already codified as Tier 3 in `DECISIONS.md` — this file doesn't
  change that, it explains why the rule exists: a SPEC's whole value is
  that its boundary is trustworthy; every silent expansion erodes that
  trust for the next reader.
- **Over-specification masquerading as thoroughness**: a PLAN.md that
  reads like pseudocode removes the AI's ability to make sound
  implementation-level judgment calls and makes the PLAN itself brittle
  (it now has to change every time a trivial internal detail does).
  Keep PLAN.md at the decisions-and-contracts level per
  `constitution.md`'s own stated reasoning for why PLAN and SPEC are
  separate documents.

## The sixth phase this project must treat as mandatory: Analyze

The full SDD pipeline is six phases, not four:
**Constitution → Specify → Plan → Tasks → Implement → Analyze.** This
project's `constitution.md` names the first five explicitly but doesn't
name the sixth as its own step — that's a gap to close, not a reason to
skip it. **Analyze is a required closing gate for every feature**
(not just every task): before a feature is considered done, re-read
`constitution.md`, the feature's `SPEC.md`, its `PLAN.md`, and its
`TASKS.md` together and confirm they're still mutually consistent — no
task quietly diverged from its PLAN, no PLAN quietly diverged from its
SPEC's acceptance criteria, no implementation detail contradicts a
`constitution.md` rule. Concretely: re-check every acceptance-criterion
checkbox in `SPEC.md` against the *finished* implementation, one by one,
and only then mark them done. This is not a nicety layered on top of
TDAD — it's the step that catches the class of bug TDAD's per-task
Red/Green loop structurally cannot: a task that passed its own narrow
test while quietly drifting from the feature's actual intent.

## One-paragraph summary for a session with no time to read the rest

Specifications, not code, are this project's durable source of truth;
code is a regenerable artifact compiled from them for the current stack.
Never let an ambiguity get silently resolved by guessing — that guess is
invisible technical debt. Never treat "the SPEC didn't cover this" as
permission to decide the scope yourself — stop and ask (Tier 3).
Keep SPEC/PLAN at the decision level, not pseudocode — over-specifying
is its own failure mode. Verify independently before trusting a result
as done — a self-reported "it passed" is not verification. When in
doubt about whether a change is small enough to just make or big enough
to need to stop and ask, re-read `DECISIONS.md`'s Tier 1/2/3 framework —
it already answers this more precisely than any restatement here would.
