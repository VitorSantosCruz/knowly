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

`forkCount=1`, `reuseForks=false`, `spring.test.context.cache.maxSize=1`.
**Why:** live A/B tested — disabling this to speed up the suite produced
9 failures/errors from shared Redis captcha-velocity counters and
cross-test-class DB/context collisions, none of them real regressions,
all caused by state leaking between test classes sharing a JVM/context.
**Applies to new decisions:** don't relax this to chase build speed
without re-running the same kind of A/B comparison — the flakiness it
prevents is real and was directly observed, not theoretical.

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
