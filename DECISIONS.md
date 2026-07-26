# Decisions — architecture, code, and product rationale

> This is a **living log**, not a historical archive. Its job is to let
> any AI assistant — with no memory of any prior conversation — reason
> about *new* decisions the same way a human engineer who'd been on this
> project since day one would: understanding not just what was decided,
> but why, so the same reasoning can be applied to situations that were
> never explicitly written down. When you make a decision (technical or
> product) that another AI or a future you would need this same
> reasoning for, add an entry. When you're unsure whether something
> belongs here versus `VISION.md` (product purpose) or `PROJECT_STATUS.md`
> (what's built) — architecture and code-level *why* goes here; product
> *why* goes in `VISION.md`; current state goes in `PROJECT_STATUS.md`.

## Decision-making authority — what you can decide vs. what you must ask

This is the part that matters most, because getting it wrong is worse
than getting a technical detail wrong. A real incident prompted this
section: an AI assistant working on the `authentication` feature added a
`logout` endpoint — technically solid work — but the existing SPEC had an
explicit "Out of scope: Logout... not addressed here" line, and the
assistant **edited that line to remove the exclusion and proceeded to
implement it, without ever pausing to ask.** That is exactly the kind of
decision this project does not want an AI making unilaterally, no matter
how reasonable the resulting code is. Being technically right does not
make it the assistant's decision to make.

**Tier 1 — just do it, no need to ask.** Implementation details within
an already-approved SPEC/PLAN/TASKS: how to structure a test, which
existing pattern to follow, minor refactors that don't change behavior,
formatting, following a precedent already established elsewhere in this
file or in the codebase. Also: running tests, committing completed tasks
(see constitution.md's "Commits and branches" — that's pre-authorized),
updating `PROJECT_STATUS.md`/this file as work lands.

**Tier 2 — decide, but say so and explain the reasoning.** A technical
choice with no exact precedent yet (e.g. which of two reasonable
libraries/approaches to use for something the SPEC requires but the PLAN
didn't pin down) — pick one, write down *why* in the PLAN or here, and
proceed. This is still autonomous, but it must leave a trail explaining
itself, because the next reader (human or AI) needs to be able to tell
this was a judgment call and reconstruct the reasoning, not just find
code that appeared with no explanation.

**Tier 3 — always ask first, no exceptions.** Any of the following is a
stop-and-ask, regardless of how confident the reasoning is or how small
the change looks:
- **Changing the scope of an existing, already-approved SPEC** —
  especially reversing an "Out of scope" line, but also adding a
  requirement that wasn't there, or reinterpreting one. If a task turns
  out to need something the SPEC didn't cover, that's a signal to stop
  and ask, not a green light to expand the SPEC yourself and keep going.
- **Product/business decisions** — anything in `VISION.md`'s "What's
  deliberately not decided yet," billing, self-service signup, new
  customer-facing behavior that isn't a straightforward implementation
  of an approved requirement.
- **Security/privacy tradeoffs** that aren't already covered by an
  established pattern in this file (e.g. a new kind of data exposure, a
  new exemption from an existing safeguard like CSRF or tenant
  isolation).
- **New external dependencies** (a new library, a new third-party
  service, a new framework) not already used elsewhere in the stack.
- **Anything hard to reverse**: schema changes that lose data, deleting
  something, changing production configuration, anything affecting
  another tenant's data or isolation guarantees.

If you're not sure which tier something falls into, treat it as Tier 3.
Asking when it turns out to have been fine costs one exchange; deciding
unilaterally when it turns out not to have been fine costs trust and
possibly real rework.

## Architectural decisions (with rationale)

Each entry: what was decided, why, and — where useful — how to extend
the same reasoning to a new, similar decision.

### Multi-tenancy is enforced at the ORM layer, fails closed

A Hibernate `@Filter` scopes every tenant-owned query to the active
tenant; a query made with no active tenant in context returns nothing
rather than erroring in a way that could be caught and ignored. **Why:**
this is the actual product boundary (see `VISION.md`) — one company's
data must never leak into another's answers, and a fail-open bug here is
the worst possible failure mode for this product specifically. **Applies
to new decisions:** any new tenant-owned entity must go through this same
filter mechanism; don't invent a parallel scoping mechanism (e.g.
manually adding a `WHERE tenant_id = ?` per query) — it's easy to forget
in one place and that's exactly the failure mode this pattern exists to
prevent.

### Staff can act as any tenant without holding a membership

`TenantService.listAllTenants`/`requireTenant` + `switchActiveTenant`
branching on `tenantContext.isStaff()`. **Why:** ConectaByte staff
operate the platform *for* tenants (onboarding, support) — see
`VISION.md`'s "why the architecture looks the way it does." This was
built reactively after a live account with zero memberships got stuck
behind `TENANT_SELECTION_REQUIRED`, but reflects a real, ongoing
operational need, not just a bug patch. **Applies to new decisions:**
staff bypass permission checks (`PermissionAspect`) but never tenant data
isolation (the Hibernate filter still applies once they've switched) —
if a new feature needs a staff-only capability, follow this same split:
bypass authorization, never bypass isolation.

### CSRF exemption is granted only to pre-authentication endpoints

`SecurityConfig`'s `csrf().ignoringRequestMatchers(...)` lists only
endpoints reachable before a session/CSRF token exists (login-request,
login-code/verify, login-password/verify) plus a couple of legacy
tenant/onboarding endpoints. **Why:** CSRF protection only makes sense
once there's an authenticated session to attack; endpoints that
establish that session in the first place have no token to check yet.
**Applies to new decisions:** a new authenticated endpoint (like
`/api/auth/logout`) is never added to this list — it goes through normal
CSRF enforcement like any other authenticated POST. Only add to this
list if the endpoint is provably reachable pre-authentication; if in
doubt, this is a Tier 3 (security tradeoff) — ask first.

### Maven Surefire runs with full per-class isolation

`reuseForks=false`, `spring.test.context.cache.maxSize=1`, `forkCount=2`.
**Why:** live A/B tested — disabling per-class isolation (i.e. letting
forks be reused, or letting the Spring context cache grow) to speed up
the suite produced 9 failures/errors from shared Redis captcha-velocity
counters and cross-test-class DB/context collisions, none of them real
regressions, all caused by state leaking between test classes sharing a
JVM/context. `reuseForks=false` and `cache.maxSize=1` are the load-bearing
settings here and must not be relaxed without re-running the same kind of
A/B comparison — the flakiness they prevent is real and was directly
observed, not theoretical.

`forkCount` itself is a different knob (how many such isolated forks run
*concurrently*, not whether any one fork's state is reused) and was
re-tested 2026-07-25: full suite (33 classes) at `forkCount=1` took
~14m10s. `forkCount=2` (still `reuseForks=false`) passed clean across two
full-suite runs (~12m25s, ~11m57s — a real but modest ~12-15% win, less
than the ~20% seen on a smaller subset during the initial A/B, likely
because the box's 8 cores get more contended as more of the 33 classes'
Postgres+RabbitMQ+Redis+LGTM+MinIO container stacks run at once).
`forkCount=4` was also tried and rejected: no further speedup (box
saturated) and it intermittently (1 of 2 runs) hit a genuine concurrency
bug — see below — that `forkCount=2` never triggered in any run.
**Applies to new decisions:** if raising `forkCount` further is tempting
later, don't just bump the number — repeat this same multi-run A/B
(≥2 full-suite passes, not one lucky run) and watch specifically for (a)
the Redis/DB collisions this entry originally described, and (b) new
resource-contention failures like the JTE one below, which only appear
under real concurrency and won't show up in a forkCount=1 sanity check.

### `${VAR:?message}` is NOT a real Spring "required property" syntax

Discovered 2026-07-25 from a real, reproduced bug: several properties in
`application.yaml` (`bootstrap-staff-email`, `spring.data.redis.password`,
`spring.ai.openai.api-key`, `knowly.auth.captcha.turnstile-secret`,
`knowly.storage.access-key`/`secret-key`) used
`${SOME_ENV_VAR:?SOME_ENV_VAR is required}`, apparently modeled on
`compose.yaml`'s use of the same syntax (which *is* valid there — Docker
Compose really does treat `${VAR:?msg}` as "fail if unset, with this
message"). **Spring's own property placeholder resolution does not
special-case `?` at all** — `${VAR:default}` just uses everything after
the first `:` as a literal default string. So when the env var was
unset, Spring silently used the literal string `"?SOME_ENV_VAR is
required"` as the actual property value instead of failing — for
`bootstrap-staff-email` this meant the `staff-bootstrap-user` migration
inserted a `User` row with that literal string as its email, which
looked like a legitimate row until someone opened the `users` table and
noticed. Verified empirically with a two-line `PropertyPlaceholderHelper`
test (`${KNOWLY_BOOTSTRAP_STAFF_EMAIL:?KNOWLY_BOOTSTRAP_STAFF_EMAIL is
required}` resolves to the literal string, not an exception, when the
env var is absent).

**Fix applied**: every one of those properties now uses bare
`${SOME_ENV_VAR}` with **no** default — Spring Boot's real behavior for
a placeholder with no default and no matching property is to throw
`PlaceholderResolutionException` at context startup, which is genuine
fail-fast behavior, verified against the actual (not assumed) resolution
semantics rather than copied from a different tool's syntax.

**Applies to new decisions:** never assume a placeholder/templating
syntax works the same across tools just because the tokens look similar
(`${VAR:?msg}` reads naturally as "required" but only *is* required
syntax in `docker compose`/shell parameter expansion, not in Spring
property resolution, not in Flyway's own placeholder syntax, etc.).
Before relying on a "fails if missing" property mechanism, verify it
actually throws for the specific resolver in play — a quick standalone
test (as done here) is cheap insurance against a properties file that
looks defensive but silently isn't.

### Background `mvnw` verification must capture the real process exit code, never `| tail`

Discovered 2026-07-25: multiple `time ./mvnw ... 2>&1 | tail -N` background
verification runs this session reported "exit code 0" (via the
run_in_background tool's own completion status) even when the test suite
had a real, deterministic failure inside it. The reason: `$?` after a
shell pipeline reflects the **last command in the pipe** (`tail`, which
virtually always exits 0) unless `pipefail` is set, and it wasn't. This
silently hid a genuine bug for at least one full round of "the suite is
green" confirmations
(`PermissionAspectTest.staffBypassesTheCheckRegardlessOfTenantContext`
had been broken since the `staff-rbac-split` commit —
`tenantContext.setStaff(true)` no longer satisfies
`PermissionAspect`'s `isStaffAdmin()` bypass check post-split — and every
tail-piped background run kept reporting success anyway).

**Applies to new decisions:** any verification run whose sole purpose is
"tell me if this passed" must capture the actual command's exit status
directly — redirect output to a file and check `$?` right after that
command (`cmd > file 2>&1; echo $?`), not through a pipe to `tail`/`grep`/
anything else that would become the "last command" and launder the real
exit code. `| tail -N` is fine for *displaying* a preview of output
you're about to read yourself, but never for the thing whose exit code
you intend to trust as a pass/fail signal.

`src/test/resources/application-test.yaml` sets `gg.jte.development-mode:
false` and `gg.jte.use-precompiled-templates: true`, overriding main's
`gg.jte.development-mode: true` (`application.yaml:2-3`, meant for local
dev hot-reload). **Why:** discovered while re-validating `forkCount` above
— JTE's dev-mode on-demand compiler writes generated `.java` files to a
`jte-classes/` directory resolved relative to the process's CWD, which is
the same absolute path for every Surefire fork (forks don't get their own
CWD). At `forkCount=4`, two forks racing to compile the same on-demand
template corrupted each other's generated source
(`gg.jte.TemplateException: Failed to compile template ...
JtenewonetimepasswordGenerated.java`), failing `MailService`-dependent
tests (`TenantSessionIntegrationTest`,
`ConversationControllerIntegrationTest`) about half the time. Since
`jte-maven-plugin` already precompiles every template at build time into
`target/classes` (`pom.xml:311-330`), tests have no need for dev-mode's
runtime compilation at all — using the precompiled templates removes the
race entirely instead of just narrowing its window. **Applies to new
decisions:** any other feature that's convenient for local dev (hot
reload, on-demand codegen, writing to a fixed relative path) should be
assumed CWD/fork-unsafe under concurrent Surefire forks until proven
otherwise — prefer the build-time-artifact path in tests, the same
reasoning as this entry.

### `minio-init-permissions` one-shot container before MinIO starts

**Why:** `cap_drop: ALL` + non-root `user: 1000:1000` on a fresh
root-owned named volume means MinIO's own entrypoint can't chown its own
data directory (confirmed by reading the real entrypoint script) — a
root, `CHOWN`-only init container run once beforehand is the minimal fix
that doesn't weaken the hardening on the actual `minio` service.
**Applies to new decisions:** the same one-shot-root-init-container
pattern is the right fix any time a hardened (`cap_drop: ALL`, non-root)
service needs one-time root-level setup on a fresh volume — don't loosen
the service's own capabilities/user instead.

### `pgvector.dimensions` pinned explicitly (1536)

**Why:** without it, `PgVectorStore#afterPropertiesSet` calls the real
OpenAI embeddings API just to infer the vector size on every startup —
wasteful, and fragile (a rate limit turns into an app-startup failure).
1536 matches `text-embedding-3-small`, the embedding model actually used.
**Applies to new decisions:** any config that would otherwise trigger a
real external API call as a side effect of *starting up* (as opposed to
serving an actual feature request) should be pinned/mocked instead —
startup should never depend on a third-party API being reachable and
happy.

### Frontend adopts PrimeNG as its component library (2026-07-25)

The app owner decided, explicitly and after multiple rounds of
hand-rolled Tailwind components (buttons, menus, cards, forms) looking
inconsistent/amateurish, to fully migrate `knowly-app/`'s interactive UI
to **PrimeNG** (+ **PrimeIcons**) — a real component library, replacing
hand-built components entirely, not partial adoption. This is a Tier 3
decision (new external dependency) — the entry below records the
decision as already made by the owner, not one an AI assistant decided
on its own. **Why:** consistency and polish are the actual product
problem being solved; a component library removes the need to
reinvent button/menu/card/form/table behavior and accessibility from
scratch every time a new screen is built. Package versions: `primeng@22.0.0`,
`@primeuix/themes@3.0.0` (the theming package — note `@primeng/themes`,
the more commonly documented name, is still pinned at `21.0.4` and is
*not* compatible with `primeng@22`'s peer deps; `@primeuix/themes` is
the correct package for this Angular major), `primeicons@8.0.0`,
`@angular/cdk@22.0.0` (required peer). The existing "Ink and Signal"
brand (`ink-*`/`signal-*` Tailwind tokens in `styles.css`) is preserved
by mapping it into a custom PrimeNG preset (`definePreset`) rather than
accepting PrimeNG's default palette — see
`knowly-app/specify/features/primeng-migration/PLAN.md` for the full
integration approach, token mapping, and screen-by-screen migration
order. **Applies to new decisions:** any new interactive UI in
`knowly-app/` checks PrimeNG's component list first — don't hand-roll a
new button/menu/card/form/table component without first confirming
PrimeNG has no suitable one. Tailwind CSS is not being replaced — it
remains the layout/spacing/utility-class tool; PrimeNG is additive for
components, not a full CSS-framework swap. A feature SPEC that assumed
a since-superseded charting library (`dashboard-analytics`'s not-yet-
approved SPEC assumed ngx-charts) should be revised to use PrimeNG's own
`Chart`/`Table` components instead, rather than adding a second charting
dependency — that revision is the PO's call, not silently made here.

## How to use this file for something new

When facing a new architectural or code-level decision with no exact
precedent above: look for the *closest* existing entry's reasoning and
ask whether the same underlying principle applies (isolation-must-never-
be-bypassed, security-exemptions-need-proof, don't-let-startup-depend-on-
external-APIs, etc.) before reasoning from scratch. If it's genuinely
novel, that reasoning is Tier 2 — decide, but write the entry here
explaining why, so it becomes precedent for whoever comes next. If it
touches scope, product direction, security tradeoffs the way described
above, or anything hard to reverse — that's Tier 3, stop and ask.
