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

### Frontend drops PrimeNG, reverts to pure Tailwind + Angular (2026-07-26)

One day after adopting PrimeNG (see the entry above), the app owner
reversed that decision: PrimeNG is being removed entirely from
`knowly-app/`, going back to hand-built Angular standalone components
styled with Tailwind CSS utility classes only. This is a Tier 3
decision (removing an external dependency, reverting a prior Tier 3
decision) — recorded here as already made by the owner, not decided by
an AI assistant. **Why:** the owner's stated trigger was noticing
icon inconsistency and concluding the interim solution ("system
icons") was unprofessional; on inspection this diagnosis was partly
imprecise — the icons in use were PrimeIcons (a font-based icon system,
not OS-native icons, and consistent across browsers/OSes by
construction) — but the owner's underlying decision to drop PrimeNG as
a library stands independent of that detail and was reconfirmed
explicitly after the correction was raised. **Icon replacement:**
PrimeIcons is replaced by **Lucide** (`lucide-angular`) — SVG-based,
tree-shakeable, no icon-font dependency. **Applies to new decisions:**
new interactive UI in `knowly-app/` is hand-rolled with Tailwind
utility classes (buttons, menus, cards, forms, tables) — do not add
PrimeNG or introduce another component library without a fresh Tier 3
decision recorded here first. See
`knowly-app/specify/features/primeng-removal/PLAN.md` for the
component-by-component removal order and Tailwind-equivalent patterns
for what PrimeNG previously provided (theme preset, menus, tables,
charts, dashboard tiles). The dashboard chart components (currently
using PrimeNG's `Chart`) need a replacement charting approach — chosen
in that PLAN, not silently defaulted here.

## `dashboard-analytics` (backend): UTC calendar-day bucketing, no tenant timezone

The new time-series metrics endpoints
(`/api/tenants/metrics/{conversations,messages,articles}/timeseries`)
bucket `created_at` by **UTC calendar day**
(`date_trunc('day', created_at)::date` at the Postgres layer), not
tenant-local day. `Conversation.createdAt`, `Message.createdAt`, and
`Article.createdAt` are all `java.time.Instant` (UTC instants); `Tenant`
has no timezone column or concept anywhere in the schema today.
Introducing tenant-local bucketing would require a new
`Tenant.timezone` field plus DST-aware conversion logic that nothing in
that SPEC asked for. UTC calendar-day is therefore the correct default:
it's what the data already is, it's what every other timestamp in this
codebase already assumes, and it avoids inventing tenant-timezone
schema/config out of scope. **Applies to new decisions:** any future
per-day/per-week bucketing feature (for any entity) should default to
UTC calendar-day bucketing the same way, unless/until a `Tenant.timezone`
field is deliberately introduced as its own Tier 3 decision.

A related, smaller judgment call made during implementation (not
pre-specified in the PLAN): for `period=all`, the zero-count-day merge
is skipped entirely — the response contains only the calendar days that
actually have at least one row, sorted chronologically, rather than a
zero-filled range back to some arbitrary "beginning of time." Zero-fill
only applies to the bounded periods (`7d`/`30d`/`90d`), where the exact
calendar range is well-defined (last N days including today). This was
a Tier 2 call made because the PLAN specified the zero-fill mechanism
but didn't pin down what date range "all" should zero-fill against
(there's no natural lower bound); flag if a future consumer needs
`period=all` to also be zero-filled from the tenant's/data's earliest
activity date.

## `auth-audit-logging`: `@AuditLog`'s actor resolution runs after `proceed()`, so it can't be used on handlers that clear auth state themselves

`AuditLogAspect.record` resolves `actorUserId` from
`SecurityContextHolder` **after** `joinPoint.proceed()` returns. For
`AuthController`'s `login-code/verify` and `login-password/verify`, this
is exactly right — `establishSession(...)` sets the `SecurityContext`
before the method returns on the success path, so `@AuditLog` correctly
captures the real actor on success and `null` on every pre-auth/failure
path (which throws before `establishSession` runs). For `logout`,
though, the opposite happens: `SecurityContextLogoutHandler.logout(...)`
**clears** the `SecurityContext` before the method returns, so an
`@AuditLog` on `logout` would always record `null` for `actorUserId`,
silently breaking the "real actor on logout" requirement. **Fix
applied:** `logout` does a manual `AuditEventRepository.save(...)`,
resolving the actor from `SecurityContextHolder` *before* invoking the
logout handler, instead of using `@AuditLog`. **Applies to new
decisions:** before putting `@AuditLog` on any handler, check whether
that handler establishes or clears authentication state as part of its
own body — if it clears it, `@AuditLog` will record the wrong (`null`)
actor and a manual write is required instead; if it establishes it
partway through, `@AuditLog` works correctly as long as the state change
happens before the method returns.

## `auth-audit-logging`: `AuditEvent.metadata` is the generic home for aspect-derived request context, starting with `sourceIp`

`AuditEvent.metadata` (a JSON column) existed on the entity but was
never populated by `AuditLogAspect` before this feature needed to record
the request's source IP for authentication events. Rather than adding a
dedicated `source_ip` column that every other `@AuditLog` consumer
(tenant/staff/article/conversation actions) would have to ignore, the
aspect derives `sourceIp` generically from
`RequestContextHolder.currentRequestAttributes()` and writes it into
`metadata`. **Applies to new decisions:** future per-event contextual
data the aspect can derive ambiently (not supplied by the annotated
method's own arguments) belongs in this same `metadata` JSON blob,
appended as additional keys, rather than as a new dedicated column —
reserve a new column only for a field that needs to be indexed or
queried directly (e.g. `WHERE` clauses, `GROUP BY`), which `metadata`'s
JSON storage doesn't support as cleanly.

**Amended 2026-07-26 (see the entry directly below): this no longer
fires for every `@AuditLog`-produced event system-wide, and the IP
itself is masked, not raw** — the original "every event, not just
auth's" scope and the raw-`getRemoteAddr()` capture described above were
reverted after an AppSec review. Read the amendment entry for the
current, correct behavior; this entry stands only for the *shape*
of the mechanism (metadata as the generic home for aspect-derived
context), not the scope or masking decision.

## `auth-audit-logging`: source IP capture is masked (/24 or /48) and scoped to auth events only, not system-wide raw capture (2026-07-26)

An AppSec review of the entry above flagged two problems with what had
shipped: (1) `AuditLogAspect.resolveSourceIp` captured `getRemoteAddr()`
**verbatim** — a genuinely new, unmasked PII type — into `metadata`,
and (2) it did so for **every** `@AuditLog`-annotated action
system-wide (tenant, staff, article, conversation), even though the
SPEC that motivated it (`auth-audit-logging`) only ever scoped "capture
source IP" to the four auth endpoints; extending the shared aspect to
every consumer was a judgment call the SPEC never asked for. AppSec
presented four options (mask+scope / raw+scope / raw+system-wide as-is
/ drop entirely) and the product owner explicitly delegated the choice
("leve para os agentes decidirem, não me pergunte nada") rather than
picking one — this entry records that delegated decision, not one made
unilaterally without the PO's sign-off.

**Decision: mask + scope to auth only** (option a). `sourceIp` is
truncated before it's written — IPv4 to its `/24` (last octet zeroed,
e.g. `203.0.113.0`), IPv6 to its `/48` (last 80 bits zeroed) — via a new
`PiiMasker.maskIp(String)`, the same module that already masks email for
this exact reason. Capture is scoped to the four `AuthController`
endpoints this feature actually covers (`login-request`,
`login-code/verify`, `login-password/verify`, `logout`'s manual write),
not extended to every `@AuditLog` consumer codebase-wide; `AuditLog`
gains a `captureSourceIp` boolean attribute (default `false`) that only
the four auth annotations/manual writes set `true` — `AuditLogAspect`
only resolves/writes `metadata.sourceIp` when that flag is set.

**Why:** this project already has an established precedent
(`PiiMasker.maskEmail`) that raw PII does not belong in a permanent,
queryable log/audit column even for legitimate operational purposes —
a truncated identifier (subnet/allocation-block granularity) still
carries essentially all the forensic value auth abuse detection needs
(same-network repeated attempts, geographic/ISP-level correlation,
distinguishing "many attempts from one place" from "credential-stuffing
from everywhere") without pinning down an exact device/individual the
way a full IP can. Scoping capture back to auth-only matches the
SPEC's actual, approved scope (`auth-audit-logging`'s own PLAN never
asked for this to become a system-wide capability) — extending a shared
mechanism's blast radius to tenant/staff/article/conversation actions
is exactly the kind of scope expansion this file's Tier 3 rules exist
to catch, and it happened here as an unreviewed side effect of "it's
convenient to put it in the shared aspect," not a deliberate decision
that those other consumers' data needed IP capture too. If article/
conversation/tenant actions later have their own genuine security
justification for IP capture, that's a fresh SPEC/PLAN decision for
that feature, not something that should have piggybacked silently on
auth's.

**Applies to new decisions:** (1) any future PII field being added to a
shared, cross-feature logging/audit mechanism must be masked using the
same pattern as `PiiMasker` (truncate/hash, never raw) unless a
specific, reviewed justification for the raw form is documented
alongside it — "it's technically easy to capture more" is not that
justification. (2) A mechanism instrumented for one feature's SPEC
(e.g. `@AuditLog`'s `metadata`) should default to *not* firing for
other consumers unless those consumers' own SPECs asked for it — add an
explicit opt-in flag (as done here with `captureSourceIp`) rather than
silently defaulting a shared aspect's new behavior to "on for
everyone" just because the column already exists and is convenient to
populate.

## `tenant-membership-acceptance`: "scoped by caller identity, not by an already-active tenant" methods must stay outside `@Transactional`/`TenantFilterAspect`

`TenantFilterAspect` enables Hibernate's `TenantFilter` strictly from
`TenantContext.getActiveTenantId()` (session state) on every
`@Transactional` service method — there is no per-call override, and a
non-staff caller with no active tenant selected gets the filter enabled
with `NO_ACTIVE_TENANT_SENTINEL`, which fails closed (returns nothing).
This is already why `TenantService.resolveSessionOutcome`/
`listOwnMemberships`/`requireActiveMembership` are deliberately **not**
`@Transactional` — they're scoped by the caller's own identity (and, for
the last one, an explicit `(user, tenantId)` pair the caller is proving
they belong to), not by whatever tenant happens to be active in the
session, so wrapping them in the aspect-driven filter would incorrectly
exclude the exact row the query exists to find.

`tenant-membership-acceptance`'s `NotificationService` (accept/decline a
pending `TenantMembership` invitation, REQ-5/REQ-7) hits the identical
shape: the invitee is, by definition, acting on a tenant they have not
yet switched into — that's the whole point of an invitation. Rather than
inventing a new isolation exemption (e.g. activating the filter for an
arbitrary tenant id pulled from request data, which would be a genuine
new Tier 3 safeguard exemption), `NotificationService`'s `listMine`,
`accept`, and `decline` reuse the exact same established pattern:
non-`@Transactional`, caller-identity-scoped repository calls, mirroring
`requireActiveMembership`'s shape exactly. Tenant isolation itself is
never weakened — the only thing this unlocks is "the caller can act on a
specific membership row that is provably their own (by recipient-user
match on the referencing `Notification`), before selecting that tenant,"
never cross-tenant listing or another user's row.

**Applies to new decisions:** before writing a new
`@Transactional`/aspect-wrapped method that needs to read or mutate a
tenant-owned row on behalf of a user who hasn't (or can't yet have) an
active tenant selection for that row's tenant, check whether the query
is actually scoped by **caller identity** (an owned FK, a provable
`(user, tenant)` pair from an unambiguous non-tenant-scoped anchor like a
`Notification.recipient`) — if so, follow this same
non-`@Transactional` pattern rather than reaching for a new filter
exemption. If the query can't be scoped that tightly by caller identity
alone, that's a real Tier 3 isolation-exemption question, not a
free pass to copy this pattern.

## `identity-profile-model`: CPF/RG blind index accepted over encrypted-column uniqueness (confirmed 2026-07-26)

**Decision (Tier 3, confirmed by the product owner 2026-07-26):**
`cpf`/`rg` are encrypted at rest with randomized-IV AES-GCM (via a JPA
`AttributeConverter`), which makes the encrypted column itself unusable
for DB-level uniqueness (same plaintext → different ciphertext every
time). To still satisfy REQ-2's "globally unique, enforced by the
database" requirement, a second, indexed `cpfBlindIndex`/`rgBlindIndex`
column stores a keyed HMAC-SHA256 of the normalized plaintext (strip
non-digits), computed with a key independent from the encryption key.
The blind-index columns carry the actual unique constraints; the
encrypted columns are never read for equality. **Why:** this is the
standard, well-established resolution to "encrypted at rest AND
DB-enforced-unique" being otherwise mutually exclusive with randomized
IVs — the alternative (deterministic encryption) has the identical
equality-revealing property with weaker cryptographic guarantees, so it
isn't a meaningfully safer choice. **The accepted tradeoff, explicitly
flagged and confirmed, not silently absorbed**: a blind index reveals
*that* two `User` rows share the same CPF/RG (an equality fact) even
though it never reveals the value itself — this is new information
exposure beyond what "encrypt CPF/RG at rest" originally promised, and
the product owner accepted it as the cost of also getting DB-enforced
uniqueness. **Applies to new decisions:** any future field that needs
both "encrypted at rest" and "DB-enforced unique" hits this exact
conflict — don't silently pick blind-indexing (or any equality-revealing
alternative) without this same explicit confirmation each time; the
tradeoff being accepted once for CPF/RG does not pre-approve it for a
different field with different sensitivity.

## `identity-profile-model`: a second, independent secret pair (encryption key + HMAC key) follows the existing `${VAR}`-from-environment convention

The blind-index mechanism above needs two cryptographically independent
keys (the `AttributeConverter`'s AES key, and the blind index's HMAC
key) — if they were the same key, an attacker who recovered one would
recover both, defeating the point of splitting them. **Decision:** both
are sourced exactly like every other secret already in
`application.yaml` (bare `${CPF_RG_ENCRYPTION_KEY}` /
`${CPF_RG_HMAC_KEY}`, no `${VAR:?msg}` — see this file's existing entry
on why that syntax doesn't do what it looks like in `application.yaml`),
bound via a new `@ConfigurationProperties(prefix = "knowly.identity")`
class. **Why:** no new pattern needed — this project already has a
working, reviewed convention for "secret sourced from environment, never
hardcoded/committed" (`OPENAI_API_KEY`, `TURNSTILE_SECRET_KEY`, MinIO
credentials); a second secret pair for one feature doesn't warrant
inventing anything new. **Applies to new decisions:** any future feature
needing more than one independent secret should default to this same
shape (one `${VAR}` per secret, grouped under its own
`@ConfigurationProperties` prefix) rather than reusing an unrelated
feature's key for convenience, or introducing a different
secrets-sourcing mechanism (e.g. a vault client) without that being a
separate, explicit Tier 3 dependency decision first.

## `identity-profile-model`: profile-edit authorization lives as explicit service-layer logic, not a single `@RequiresPermission`/`@RequiresGlobalPermission` annotation

REQ-9 through REQ-14a's actual rule set ("admin bypass regardless of
self/other, OR permission-holder allowed on *others only*, rejected on
self") depends on both *who* holds what *and* whether the target is the
caller — something no single `@Requires*` annotation call expresses,
since those aspects only ever check "does the caller hold permission X,"
not "and is the target someone other than the caller." **Decision:**
`UserProfileController`'s edit/view endpoints carry no
`@RequiresPermission`/`@RequiresGlobalPermission` annotation;
`UserProfileService` implements the full decision tree itself
(`STAFF_ADMIN`/`MEMBER_ADMIN` bypass checked directly, then
tenant-scoped/global-scoped `PROFILE_EDIT`/`PROFILE_VIEW` checked via the
existing `PermissionService`/`GlobalPermissionService`, plus an explicit
self-exclusion guard for the non-admin permission paths), throwing the
existing `PermissionDeniedException` for every rejected path so the
existing 403 mapping and `@AuditLog`-on-failure behavior both still
apply unchanged. **Why:** this mirrors the precedent already set by
`NotificationService` (its recipient-identity check doesn't fit
`PermissionAspect`'s tenant-membership model either, so it's inline
service logic, not a forced-fit annotation) — the underlying principle
("if the SPEC's authorization rule genuinely doesn't reduce to a single
permission-grant check, do it explicitly in the service, don't stretch
the aspect to cover it") already exists in this codebase, just not
documented as reusable precedent before now. **Applies to new
decisions:** before adding a new `@Requires*`-annotated endpoint, check
whether the actual rule is "does the caller hold permission X" (fits the
aspect) or something richer involving self/other, ownership, or multiple
independent bypass paths (doesn't fit) — the latter belongs as explicit,
testable logic inside the service method, not as a stretched annotation
parameter or a second competing aspect.

## `identity-profile-model`: reuses `Notification` by relaxing its existing `tenant_membership_id` FK to nullable, rather than building a second notification mechanism

`tenant-membership-acceptance`'s `Notification` entity has
`tenantMembership` as `@ManyToOne(optional = false)` — every row today
is anchored to a `TenantMembership`. This feature's profile-edit-request
notifications have no such anchor (approver may be `STAFF`/global-permission-only,
requester may themself have no membership row in scope). **Decision:**
add a new nullable `profile_edit_request_id` FK to `notifications`,
relax `tenant_membership_id` to nullable, and add a `CHECK` constraint
enforcing exactly one of the two is set per row — rather than
duplicating a second in-app-notification table/mechanism for this
feature alone. **Why:** confirmed by reading every existing
`Notification` consumer (`NotificationService`,
`NotificationController`) that none dereferences `.getTenantMembership()`
without already being on a membership-invitation-only code path, so
relaxing the column's nullability doesn't silently break an existing
assumption — it only makes room for a second, equally legitimate anchor
type. Building a second notification mechanism instead would have meant
two inboxes, two read/resolve flows, and two things a user has to check
for "what's pending for me," directly contradicting the SPEC's own
non-functional requirement ("reuses `tenant-membership-acceptance`'s
`Notification` entity rather than building a second mechanism").
**Applies to new decisions:** a shared entity introduced by one feature
can be extended by a later feature via an additional nullable
FK + a `CHECK` constraint enforcing mutual exclusivity, *provided* every
existing consumer of the entity is actually read first to confirm none
of them assumes the relaxed column is always non-null — do not relax a
NOT NULL constraint on a shared table without that verification step
recorded in the PLAN, the way this entry does.

## `staff-global-dashboard`: `metric-tile.component.ts` gains an additive "pre-fetched value" presentational mode

`metric-tile.component.ts` (from `dashboard-analytics`) was built as a
self-fetching component: every instance owns its own `MetricFetcher`,
calling its own `url` with its own `period`, and renders its own
loading/error/permission-denied state independently per tile — correct
for the tenant-scoped dashboard's five tiles, each backed by a distinct
timeseries/point-in-time endpoint with per-tile-independent failure
semantics (`dashboard-analytics`'s SPEC req. 9: one failing tile can't
blank the rest). `staff-global-dashboard`'s four global-metrics tiles
don't fit that shape: all four numbers come from a **single**
`GET /api/staff/metrics/global` call, and that SPEC's REQ-5 explicitly
wants the 403/network failure handled **once, at the page level**, not
per-tile — four independent self-fetching tiles would mean four
redundant HTTP calls for data that's already a single response, and four
separate (necessarily identical) error-state renders where the SPEC
wants exactly one.

**Decision:** rather than forking a second, near-identical tile
component, `metric-tile.component.ts` gains two new, additive,
optional inputs — `value` (a pre-computed number; when set, skip
self-fetching and any sparkline chart/table entirely) and `disabled`
(renders a static "coming soon" label, no fetch attempted). A third
input, `loading`, was added alongside these initially but removed
during `qa-test-automation`'s final review as dead code — the
consuming page already gates tile rendering on its own loading state
before mounting any tiles, so a per-tile loading input had no code path
that ever read it.
`url`/`valueSelector`/`period` become optional rather than required, but
the self-fetching code path (used unchanged by all five existing
tenant-scoped tiles) is untouched — this is additive, not a breaking
change to any existing call site.

**Why extend rather than fork:** identical reasoning to
`dashboard-analytics`'s own PLAN for extending `createMetricFetcher`
instead of writing a second fetch helper — a second near-identical
presentational component would violate this app's "one shape per
concern, don't duplicate" convention (see also `PermissionsService`/
`GlobalPermissionsService`/`ActiveTenantService`/`AuthService` all being
variations on one state-service shape) and double the surface that has
to be kept visually/behaviorally in sync going forward.

**Applies to new decisions:** before adding a new self-fetching widget
component whose data actually comes from a call a *parent* already
made (single endpoint serving multiple displayed values, or a
page-level error-handling requirement rather than per-widget), check
whether the existing self-fetching component can be extended with an
optional "pre-fetched value" presentational mode the same way, rather
than either (a) forking a new near-duplicate component, or (b) forcing
a page-level data shape into N redundant per-widget HTTP calls just to
reuse the existing self-fetching path unmodified.

## `tenant-pagination-search`: first page/size pagination contract — `@Query`+`Pageable` over `Specification`, sort fixed server-side, envelope DTO stays local until a second consumer exists

This is the first page/size pagination endpoint anywhere in this
codebase (`GET /api/tenants`, previously an unbounded
`tenantRepository.findAll()`). No existing precedent to copy —
`staff-user-listing` stayed deliberately unpaginated, `staff-audit-trail-view`
uses a hard row cap, not offset pagination. Three judgment calls had no
exact analog and were decided here rather than escalated, since none
touch scope, security, or a new dependency (all Tier 2 per this file's
authority section):

1. **DB-level filtering across three OR'd fields uses a single JPQL
   `@Query` + `Pageable` on the repository, not a `Specification`.**
   `staff-user-listing` chose two explicit derived methods over a
   `Specification` for one optional filter; three OR'd fields plus an
   optional presence check can't be named cleanly as a derived method,
   but the criteria never combine dynamically (search is present or
   absent, never partial/composable) — the actual justification for a
   `Specification` (dynamically composed criteria) doesn't apply here,
   so one `@Query` with a `:search IS NULL OR ...` guard covers both the
   filtered and unfiltered case in one method.
2. **Sort order is built by the service (`Sort.by("name").ascending()`
   passed into the `Pageable`), never taken from a client-supplied
   parameter, and not duplicated as an `ORDER BY` inside the `@Query`
   string.** Spring Data appends a `Pageable`'s `Sort` as `ORDER BY`
   automatically for a `@Query` method that doesn't declare its own —
   one source of truth for "alphabetical by name is the only supported
   order," matching this SPEC's explicit no-custom-sort scope.
3. **The response envelope (`PageResponseDto<T>`) lives in the
   `tenancy.dto` package that owns its only consumer, not a new
   top-level `common`/`shared` package**, even though the SPEC frames
   this shape as the intended default template for future paginated
   endpoints. This codebase has no existing shared/common package to
   extend, and creating one for a type with exactly one consumer is
   premature structure ahead of an actual second use.

**Why:** in each case, the more "generic-looking" tool
(`Specification`, a client-controlled sort param, a new shared package)
would add real surface area to solve a problem that doesn't exist yet
here — dynamic criteria composition, configurable sort, or a second
consumer. YAGNI over premature generality, same principle as
`dashboard-analytics`'s CSV-export decision (hand-built CSV over a new
library dependency for ~7 known columns).

**Applies to new decisions:** when a future feature adds the *second*
paginated list endpoint in this codebase, that is the trigger — not
before — to (a) revisit whether a shared `Specification`-building
helper is now justified by an actual second dynamic-filter shape, and
(b) move `PageResponseDto` out of `tenancy.dto` into a shared location
and update both call sites. Until that trigger exists, don't
pre-build either piece of shared machinery off of one endpoint's shape.

## `identity-profile-model` retrofit: split `users` personal-data columns into `user_profiles`/`addresses`/`contacts`, LGPD-minimized field set, self-service restricted to `avatar_url` (confirmed with product owner, 2026-07-28)

**Status: design confirmed in conversation with the product owner,
not yet implemented.** No migration, entity, or service code exists
for this yet — `identity-profile-model` as shipped (see that feature's
own SPEC/PLAN/TASKS and `V17__add_identity_profile_fields.sql`) is
still what's actually running. This entry exists so the next
conversation (any AI) that picks up a backend retrofit has the full
reasoning without re-deriving it. Treat this as the target design for
a new backend PLAN.md, not as already-done work.

**Scope correction (2026-07-28, discovered after this design was
confirmed):** the `knowly-app` frontend feature `user-profile` was
independently implemented and committed (11 commits, `17e1b1a`
through `9293e76`, not yet pushed to `origin/main`) *against the old
`V17` flat contract* (`fullName`/`address` free-text
string/`rg`/`cpf`/`phone` on `GET/PUT /api/users/{id}/profile`) while
this redesign was being discussed — see
`knowly-app/specify/features/user-profile/`. The product owner
confirmed (2026-07-28) this redesign proceeds anyway, which means the
retrofit's scope is now bigger than originally framed: it must also
update the already-shipped frontend consumers
(`ProfileService`, `ProfileFieldsFormComponent`,
`OwnProfilePageComponent`, `ProfileSectionComponent`,
`ProfileEditRequestsInboxPageComponent`) to the new
tables-backed contract (structured address, `contacts` list instead of
a single `phone` field, `avatar_url` as the only directly-self-editable
field), not just the backend. Whoever formalizes the PLAN.md for this
should treat it as a two-subproject retrofit from the start, per
`constitution.md`'s "Feature SPEC placement" rule (two SPECs, one per
subproject) — not a backend-only change with a frontend follow-up.

**Decision (Tier 3, confirmed by the product owner 2026-07-28): three
new 1:1/1:n tables replace the flat columns `V17` added directly to
`users`.**

```sql
-- 1:1 with users. Row created eagerly at account creation (see "Why"
-- below for why this must be eager, not created on first submit).
CREATE TABLE user_profiles (
  user_id             BIGINT PRIMARY KEY REFERENCES users(id),
  full_name           VARCHAR(255),         -- nullable: eager row, empty until filled
  cpf                 VARCHAR(255),         -- encrypted, same converter as V17's User.cpf
  cpf_blind_index     VARCHAR(64),
  rg                  VARCHAR(255),         -- encrypted
  rg_orgao_emissor    VARCHAR(20),          -- NOT inside the encrypted envelope: alone it
                                             -- doesn't identify anyone, so it stays queryable
  rg_blind_index      VARCHAR(64),
  birth_date          DATE,
  avatar_url          VARCHAR(500),
  created_at/created_by/updated_at/updated_by,
  CHECK ((cpf IS NULL) = (cpf_blind_index IS NULL)),
  CHECK ((rg IS NULL) = (rg_blind_index IS NULL))
);
-- unique indexes on cpf_blind_index/rg_blind_index WHERE NOT NULL, same
-- blind-index pattern as this file's existing CPF/RG entry above —
-- unchanged, just moved to a new owning table.

-- 1:1 with users, NOT created eagerly (only once an address is actually
-- entered). Structured, not the V17 free-text VARCHAR(500).
CREATE TABLE addresses (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  cep VARCHAR(9) NOT NULL CHECK (cep ~ '^\d{5}-?\d{3}$'),
  logradouro VARCHAR(255) NOT NULL,
  numero VARCHAR(20),          -- string, not int: real addresses have "S/N", "123A"
  complemento VARCHAR(100),
  bairro VARCHAR(100) NOT NULL,
  cidade VARCHAR(100) NOT NULL,
  estado CHAR(2) NOT NULL CHECK (estado ~ '^[A-Z]{2}$'),
  pais VARCHAR(100) NOT NULL DEFAULT 'Brasil',
  created_at/created_by/updated_at/updated_by
);

-- 1:n with users. Multiple phone/whatsapp/email, replacing V17's single
-- `phone` column. users.email remains the ONLY login credential and is
-- never duplicated in here.
CREATE TABLE contacts (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  type VARCHAR(20) NOT NULL,   -- PHONE, WHATSAPP, EMAIL, OTHER
  value VARCHAR(255) NOT NULL,
  label VARCHAR(50),
  is_primary BOOLEAN NOT NULL DEFAULT false,
  created_at/created_by/updated_at/updated_by
);
CREATE INDEX idx_contacts_user_id ON contacts (user_id);
CREATE UNIQUE INDEX ux_contacts_primary_per_type
  ON contacts (user_id, type) WHERE is_primary;
-- max ~5 contacts/user enforced in ContactService, not the DB (see "Why").
```

`profile_edit_requests` (existing table, needs retrofit alongside the
above) gains flattened `proposed_*` columns mirroring `user_profiles`
and `addresses` (both 1:1, so flat columns fit the same way `V17`
already did it), plus a new child table for the 1:n contacts case,
which can't be flattened:

```sql
CREATE TABLE profile_edit_request_contacts (
  id                       BIGSERIAL PRIMARY KEY,
  profile_edit_request_id BIGINT NOT NULL REFERENCES profile_edit_requests(id),
  action                   VARCHAR(10) NOT NULL,  -- ADD, UPDATE, REMOVE
  contact_id               BIGINT REFERENCES contacts(id),  -- NULL only for ADD
  type                     VARCHAR(20),
  value                    VARCHAR(255),
  label                    VARCHAR(50),
  is_primary               BOOLEAN,
  CHECK (
    (action = 'ADD' AND contact_id IS NULL)
    OR (action IN ('UPDATE','REMOVE') AND contact_id IS NOT NULL)
  )
);
CREATE INDEX idx_profile_edit_request_contacts_request
  ON profile_edit_request_contacts (profile_edit_request_id);
```

**Why the table split:** `users` is the authentication/session table
(login, password/OTP, roles); personal data doesn't belong mixed into
it — that was the product owner's core objection to `V17`'s shape, and
it's a correct one, not just taste. `addresses` split out because
`V17`'s free-text `VARCHAR(500)` can't support the real requirement
(a legally-notifiable structured address); `contacts` split out
because a single `phone` column can't represent "a person has more
than one way to be reached" (multiple phones, WhatsApp, alternate
email).

**Why `addresses` stays 1:1, not 1:n, even though `contacts` is
1:n:** both were considered for 1:n (a person can have a home and a
work address, just like multiple phone numbers). The deciding
difference is *purpose*: every field in this model exists to serve one
stated business requirement — being able to identify/hold a user
accountable if they misuse the platform, not building a general social
profile. Multiple reachability channels (`contacts`) genuinely serve
that purpose (more ways to reach the person is strictly better for
accountability). Multiple addresses do not — they add ambiguity
("which of the 5 addresses is the real one to legally notify?") rather
than resolving it. A single current, correct address is what
accountability actually needs. If a genuine second business need for
multiple addresses shows up later (billing/shipping, e.g.), that's a
new feature with its own justification, not a retrofit of this one.

**Why the field set was cut from the first draft (LGPD data
minimization, applied field-by-field against the stated
purpose):** `social_name`, `gender`, and `nationality` were all in an
earlier draft and removed — none of them serve
identification/accountability (CPF already identifies uniquely;
`social_name` is a respectful-treatment/UX concern, not a legal-name
concern; `gender` has zero bearing on identifying a bad actor and is
sensitive-adjacent data with no stated purpose, which is exactly what
minimization exists to prevent; `nationality` is moot while the only
supported ID documents are CPF/RG, both Brazilian). `birth_date` and
`avatar_url` were *also* flagged as not serving accountability, but
the product owner explicitly chose to keep both anyway, for a
different, deliberately non-compliance reason: giving the user
something personal to attach to in the product (retention/engagement),
not identification. **This is a real distinction that matters for the
permission model below — birth_date and avatar_url are kept for two
different reasons, and only avatar_url gets the more permissive
handling.**

**Decision: permission model per field — only `avatar_url` is
self-editable directly; everything else (including `birth_date`) can
only be *proposed* by the owner via `profile_edit_requests`, never
self-approved, and never edited directly by the owner even when they'd
otherwise qualify under an existing grant.**

| Field(s) | Self direct-edit | Self self-request (pending approval) |
|---|---|---|
| `avatar_url` | Yes, unrestricted | — |
| `birth_date` | No | Yes — approval by someone else required |
| `full_name`, `cpf`, `rg`, `rg_orgao_emissor`, `addresses.*`, `contacts.*` | No | Yes — approval by someone else required |

This mostly *keeps* `identity-profile-model`'s existing REQ-15 shape
(self-request allowed, self-approval never allowed) — the only actual
change from what shipped is that `birth_date` is now explicitly
grouped with the identification fields for this purpose (not treated
like `avatar_url`) even though it was kept in the field set for a
non-identification reason. **Why:** the stated worry driving this
question was "avoid the user tampering with the data that would
identify them" — `birth_date`, `full_name`, `cpf`, `rg`, and address/
contact data are all inputs that could plausibly be manipulated to
frustrate identification if a user could silently self-edit them, even
if `birth_date` alone isn't as load-bearing as CPF for that purpose;
`avatar_url` carries no identification weight either way (it doesn't
prove or disprove who someone is), so it's the one field that's safe
to leave fully self-service. **Enforcement implication:** the existing
self-exclusion guard in `UserProfileService` (see this file's
`profile-edit authorization lives as explicit service-layer logic`
entry above) needs an explicit `resolved_by_user_id <> requester_user_id`
check/constraint on `profile_edit_requests` — today that's only
implicit in service logic; make it a DB `CHECK` too
(`CHECK (resolved_by_user_id IS NULL OR resolved_by_user_id <>
requester_user_id)`) so self-approval can't slip through a future code
path that forgets the service-layer guard.

**Decision: `contacts.is_primary` is unique per `(user_id, type)`, not
one global primary per user.** Rejected "one primary contact overall"
because the real question this field answers is "which phone number is
the main one" and "which email is the main one" *independently* — an
accountability flow may need to notify by primary email and call the
primary phone in the same incident, and a single global primary would
force picking one channel over the other for no reason tied to the
actual purpose.

**Decision: the ~5-contacts-per-user cap is enforced in
`ContactService`, not the database.** A cross-row count can't be
expressed as a Postgres `CHECK` (would need a trigger), and the limit
is a mutable business rule ("~5" was stated loosely, not as a fixed
security boundary) — a DB trigger is harder to test/maintain for a
number that's expected to possibly change than a service-layer guard.
Reserve a DB-level enforcement for this kind of rule only if it ever
becomes a real anti-abuse/security boundary where an application-layer
bypass would be a genuine risk; it isn't that today.

**Decision: this ships as a direct retrofit migration with backfill,
not a compatibility view or an expand/contract two-phase
migration.** A compatibility view was considered and rejected: there
is no external consumer of `users.cpf`/`users.address` outside this
same backend/frontend deploy (both move together, monorepo) that a
view would need to protect — it would add permanent indirection for a
problem this codebase doesn't have. An expand/contract two-phase
migration was also considered and rejected: that pattern earns its
cost when old and new app versions read the same table concurrently
during a zero-downtime rollout; nothing here suggests that
constraint, so it's process overhead without the payoff. **Sequencing
(per `data-architect-dba` review, 2026-07-28):**
1. New migration creates `user_profiles`/`addresses`/`contacts`,
   backfills `full_name`/`cpf`/`rg`/`phone` from `users` into the new
   tables (`phone` becomes a `contacts` row, not a `user_profiles`
   column — `email` is explicitly *not* backfilled into `contacts`,
   `users.email` stays the sole login credential).
2. Same or a following migration retrofits `profile_edit_requests`
   (adds the flattened address/profile `proposed_*` columns, creates
   `profile_edit_request_contacts`); any existing `PENDING` requests
   need an explicit decision (migrate vs. cancel/expire) before this
   ships — not yet made, flag for the PLAN.md that formalizes this.
3. A later migration, only after the new code path is running and
   verified, drops `users`/`users_aud`'s
   `full_name`/`address`/`rg`/`cpf`/`phone`/`rg_blind_index`/
   `cpf_blind_index` columns. **`address`'s free-text data is not
   automatically migrated to the structured `addresses` table — the
   product owner confirmed (2026-07-28) there is no real production
   data in that column worth preserving, so this is a straight drop,
   not a lossy-migration warning that needed separate handling.** If
   this is ever revisited with real data present, that confirmation
   would need to be re-obtained — it was scoped to this specific
   pre-launch state, not a standing exemption.

**Applies to new decisions:** any future personal-data field added to
this system should be run through the same test applied here —
does it serve the stated accountability purpose, or does it serve a
different, legitimate-but-separate purpose (engagement, UX, etc.)? The
former lives with `cpf`/`rg`/`full_name`/address/contacts and inherits
the request-not-direct-edit restriction; the latter needs its own
explicit self-edit-vs-request decision, not an assumption either way —
`avatar_url`'s "self-edit is fine" was earned by having zero
identification weight, not just by being non-compliance-flagged.

## `identity-profile-model-v2`: `avatar_url` uploads reuse `article-management`'s existing MinIO/S3 infrastructure, second bucket

**Decision: `avatar_url` image bytes are uploaded through a new
`AvatarStorageService`, structurally identical to the existing
`ArticleStorageService` (`S3Client`/`S3Presigner` against the
already-provisioned MinIO backend, same bucket-provisioning
`@PostConstruct` pattern), writing to a second, dedicated bucket
(`knowly.storage.avatar-bucket`) rather than the existing article
bucket, via a new `POST /api/users/me/profile/avatar` multipart
endpoint mirroring `ArticleController.upload`'s exact shape.** **Why:**
this codebase already has exactly one object-storage integration,
already hardened (`minio-init-permissions` one-shot container, see this
file's own entry on that pattern) and already handles the identical
underlying problem — accept a file, store it, return a fetchable URL.
Building a second storage mechanism for the same problem would be a new
piece of infrastructure with no justification; reusing it needs no new
dependency (same AWS S3 SDK already in `pom.xml`) and no new secret
(same MinIO credentials, one more bucket name). A second bucket, not the
shared article bucket, was chosen because avatars and article files have
genuinely different lifecycle/access needs (one small replace-in-place
image per user, generally viewable, vs. versioned tenant-permission-
gated content) — mixing them would couple two unrelated retention/access
policies for no benefit. **Applies to new decisions:** any future
"accept a file, store it, serve it back" need in this codebase should
default to a new `*StorageService` following this exact shape (own
bucket, `ArticleStorageService`'s method shape) rather than reaching for
a new library/service — a new object-storage *provider* would still be
Tier 3, but a new *bucket* against the existing provider is not.

## `identity-profile-model-v2`: existing `PENDING` `profile_edit_requests` rows are cancelled, not migrated, by the `V18` retrofit migration

**Decision: any `profile_edit_requests` row still `PENDING` when the
`V18` retrofit migration runs is marked with a new `CANCELLED` status
(not silently deleted, not attempted to be reshaped into the new
structured-address/contacts request format).** **Why:** the old row's
`proposed_address` is one free-text `VARCHAR(500)`; the new shape needs
eight independent structured address columns plus a separate
add/update/remove contacts list — there is no reliable, generic way to
parse an arbitrary free-text address string into structured fields as
part of a data migration (that's a real address-parsing/geocoding
problem, disproportionate to solve for a migration step), so any
backfill attempt would either silently drop structure or need manual
per-row review. This mirrors the reasoning the product owner already
confirmed for `users.address` itself in the parent retrofit decision
(pre-launch system, no real production data worth a lossy carry-forward)
— applied here to the sibling table that references the same shape.
Cancelling rather than deleting preserves the row for audit/history
value while making it unambiguous the request was never resolved through
approve/reject. Given `identity-profile-model` shipped only two days
before this retrofit was scoped, on a pre-launch system, this is expected
to affect zero or near-zero real rows — the decision exists for
correctness regardless of realistic volume. **Applies to new decisions:**
any future schema retrofit that changes a request/proposal table's
proposed-value shape should default to "resolve (cancel), don't attempt
a lossy structural backfill" for in-flight rows, unless a specific,
justified parsing strategy exists — silently dropping data or leaving
rows in an unreadable state are both worse than an explicit cancelled
state.

## `identity-profile-model-v2`: `contacts.type`-dependent format validation is explicit `ContactService` logic, not a custom Bean Validation `@Constraint`

**Decision: per-type format validation of `contacts.value` (an `EMAIL`
contact must look like an email, `PHONE`/`WHATSAPP` must look like a
phone number, `OTHER` unconstrained) is implemented as an explicit
method (`ContactService.validateFormat(type, value)`) called from every
write path, not as a custom `jakarta.validation` `@Constraint`/
`ConstraintValidator`.** **Why:** verified this codebase has zero
existing custom `@Constraint` classes anywhere — every validation need
so far (`LoginRequestDto`, `VerifyPasswordRequestDto`,
`UpdateArticleRequestDto`, etc.) has been satisfied by Jakarta's
built-in per-field annotations (`@NotBlank`, `@Email`). This case is
structurally different: the correct format depends on a *sibling*
field's value (`type`), which built-in annotations can't express without
a class-level custom constraint — introducing that machinery for exactly
one conditional rule would be more ceremony than this codebase's
established precedent for cross-field business rules, which is a plain
explicit check inside the owning service (`UserProfileService`'s
self-exclusion guard, `TenantService`'s admin-checks,
`NotificationService`'s recipient check — all pre-existing examples of
the same "logic that doesn't fit a single annotation lives in the
service" pattern). **Applies to new decisions:** a validation rule that
depends on more than one field on the same DTO/entity should default to
an explicit service-layer check, matching this codebase's existing
precedent, rather than introducing this project's first custom
`@Constraint`/`ConstraintValidator` — if a future need is genuinely
better served by a reusable annotation (e.g. the same cross-field rule
needed identically in many unrelated places), that tradeoff should be
weighed explicitly at that point, not defaulted into silently.

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
