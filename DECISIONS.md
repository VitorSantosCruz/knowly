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

### Test isolation comes from `DataIsolationExtension`, not from JVM-per-class forking (supersedes the old Surefire-fork-based approach)

**Superseded 2026-07-31.** This entry originally mandated
`reuseForks=false`, `spring.test.context.cache.maxSize=1`, `forkCount=2`
in `knowly-api/pom.xml` as "load-bearing" and "must not be relaxed
without re-running the same kind of A/B comparison" (see the original
rationale kept below for history). That config forced a brand-new JVM
(and therefore a brand-new Spring context and brand-new Testcontainers —
Postgres/Redis/RabbitMQ are plain non-static `@Bean @ServiceConnection`s
in `TestcontainersConfiguration`) per test class, which incidentally gave
isolation as a side effect, but made `./mvnw verify` take ~20-25 minutes.

Disabling that config (to cut verify time — it dropped to ~7min) brought
back exactly the failure mode this entry originally warned about: 97
tests failing deterministically (reproduced across 2 full-suite runs,
byte-for-byte the same failures both times) from Redis-backed
login-throttle/lockout state and Postgres rows leaking between test
classes that got assigned the same reused Spring context/containers.

**Fix applied:** `knowly-api/src/test/java/br/com/conectabyte/knowly/DataIsolationExtension.java`,
a JUnit 5 `BeforeEachCallback` auto-detected via
`src/test/resources/junit-platform.properties` +
`META-INF/services/org.junit.jupiter.api.extension.Extension` (so it
applies to every `@SpringBootTest` class without editing any of them).
Before each test method it `TRUNCATE ... RESTART IDENTITY CASCADE`s every
`public`-schema table except `flyway_schema_history`/`revinfo` (table
list read dynamically from `information_schema.tables`, so it never
drifts from the migrations), re-seeds the bootstrap staff user row at its
post-`V14` migration state (`global_role = STAFF_ADMIN`, not V13's
original `STAFF` — a naive reseed at the pre-V14 value reintroduces
exactly one failure, `BootstrapStaffUserMigrationIntegrationTest`), and
`FLUSHALL`s Redis via the `RedisConnectionFactory` bean. Verified green
across 2 consecutive full-suite runs (444/444, 0 failures, ~7:30min each)
with the Surefire fork/cache config fully removed from `pom.xml` (not
just commented out).

**Applies to new decisions:** the correctness guarantee for test
isolation now lives in `DataIsolationExtension`, not in Surefire
fork/cache settings — don't reintroduce `reuseForks=false`/
`cache.maxSize`/`forkCount` tuning as a fix for state-leak-shaped test
failures; instead check whether `DataIsolationExtension` needs to clean
up a new piece of shared state (a new Redis key prefix, a new table that
needs seeding, a new external container). If a new kind of shared state
doesn't fit the truncate-and-reseed model (e.g. a genuinely stateful
external service the extension doesn't reset), that's the signal to
extend the extension, not to fall back to JVM-per-class forking.

<details>
<summary>Original entry (2026-07-25), kept for history</summary>

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

</details>

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

## `internal-team-chat`: message history pagination uses a plain message-id cursor, not a `(created_at, id)` compound or page/size offset pagination (2026-07-31)

**Decision: chat message history (`ChatMessage`/support-channel
messages) is paginated with an opaque cursor encoding the message `id`
alone** — `base64(messageId)`, compared with `<`/`>` against `id` for
`before`/`after` requests respectively — **not** `tenant-pagination-
search`'s `PageResponseDto` page/size envelope, and not the backend
architect's own first-draft compound `(created_at, id)` cursor. **Why:**
`tenant-pagination-search` established page/size offset pagination for
this codebase's first paginated list, but that shape assumes a
materially stable list between page fetches; chat history is an
append-only feed read backward from an arbitrary scroll position while
new messages can arrive at the newest end mid-session (see the polling
decision below) — an offset-based "page 2" is not a stable concept here
(a new message shifts what offset N means). A compound `(created_at,
id)` cursor was considered next (and briefly the backend PLAN's initial
design, written independently of the frontend PLAN) but rejected on
reconciliation: `chat_messages.id` is a `BIGSERIAL`, strictly
monotonically increasing in insertion order per conversation, so it is
already a total order with no same-instant collision to break — adding
`created_at` to the cursor was defensive over-specification for a schema
that doesn't need it, and a plain id-cursor is simpler to implement,
test, and reason about on both sides of the API. **Applies to new
decisions:** any future append-only/feed-shaped list (activity feeds,
notification streams) should default to this same id-only cursor shape
rather than reusing `PageResponseDto`'s offset shape (which stays
correct for genuinely stable lists — tenant listings, user listings —
only); reach for a compound cursor only when the ordering column is
*not* already a strictly-increasing, collision-free key by itself (e.g.
ordering by a mutable or non-unique column would need a tie-breaker —
`id` alone never does).

## `internal-team-chat`: real-time message delivery is 5-second client polling of the existing paginated GET, not WebSocket/SSE — server push flagged as a future direction, not built now (2026-07-31)

**Decision: for v1, new messages from other users are delivered by the
client polling `GET .../messages?after=<lastSeenId>` every 5 seconds
while a conversation/support-channel view is open, paused when the tab
is hidden (Page Visibility API) — no server-push mechanism is
implemented.** This was independently arrived at by both the backend
and frontend PLANs (backend deferred real-time entirely to "whatever GET
the client already polls"; frontend concretely specified the 5s
interval) and confirmed as non-conflicting on reconciliation — they
describe the same mechanism from opposite ends. **Why polling over
WebSocket/SSE:** this is this codebase's first "receive updates
originating from other users while a view is open" requirement. The
only existing "live" precedent, `ConversationService.sendMessage`'s SSE
stream (`MessageStreamingService`'s `SseEmitter`), answers a single
in-flight request's own AI-completion response — it is not a registry
of long-lived per-user connections and doesn't solve "deliver a message
written by user A to an already-open session of user B." Building that
(a persistent `SseEmitter` registry keyed by user id, or WebSocket/STOMP)
is new infrastructure with real connection-lifecycle, memory, and
horizontal-scaling implications (multiple app instances behind a load
balancer need pub/sub fan-out — e.g. via the already-provisioned
RabbitMQ — to deliver to a connection held by a *different* instance),
which is a genuine Tier 3 new-dependency/new-infra decision, not
something either PLAN should decide unilaterally; the SPEC itself places
real-time transport out of scope. Polling was chosen as the lower-cost
default at this app's current scale (text chat, no sub-second-latency
requirement); the backend's messages endpoint was extended with `after`-
cursor support specifically to serve this. **Future direction (flagged,
not built):** if/when real-time push is wanted, prefer SSE-per-user
(not WebSocket) backed by RabbitMQ fan-out for multi-instance delivery,
reusing the already-provisioned broker and the existing SSE precedent
rather than adding a new protocol — build this only once an actual
latency complaint exists, not preemptively. **Applies to new decisions:**
any future "push updates to an open client session" need should default
to client polling first at this app's scale, and should evaluate this
entry's SSE-per-user-over-RabbitMQ direction (not a fresh WebSocket
design) as its starting point when polling genuinely stops being
sufficient — treat "we now have a second feature that needs this" as the
trigger to build it once, generalized, rather than adding a second
bespoke polling loop or a second incompatible push mechanism.

## `internal-team-chat` AppSec follow-up: narrow `/api/tenants/**`'s CSRF exemption to the exact pre-authentication path that needs it (2026-07-31)

**Decision: `SecurityConfig`'s CSRF exemption is narrowed from the
wildcard `"/api/tenants/**"` back down to the single exact path
`"/api/tenants/active"`.** The wildcard was introduced in
`feat(tenancy): add tenant and membership management endpoints`
("CSRF is ignored for `/api/tenants/**`, same reasoning already applied
to the auth endpoints") by pattern-matching against the earlier, correct
exemption of `/api/auth/**`/`/api/tenants/active` without checking
whether the new endpoints it covered were actually pre-authentication —
they weren't. `/api/tenants/active` is exempt because it runs
immediately after login, in the same request sequence as the exempted
`/api/auth/**` endpoints, to select the active tenant before a full
session is established. Every other endpoint nested under
`/api/tenants/**` is a normal authenticated, state-changing endpoint
(`TenantController`'s own member/permission/access-group mutations,
`ConversationController`, `ArticleController`) and was incorrectly
skipping CSRF protection as a side effect of sharing that URL prefix.
**Why this matters:** an authenticated user's browser could be tricked
into submitting a cross-site request to any of these endpoints (add a
member, grant a permission, delete an article, etc.) with no CSRF token
required, because the exemption matcher had no way to distinguish "this
is the pre-auth tenant-selection step" from "this happens to live under
/api/tenants". Fixed by listing the exact pre-auth path instead of a
prefix, and adding real `XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header
plumbing to the integration tests that had been implicitly relying on
the broad exemption (`TenantManagementIntegrationTest`,
`StaffRbacIntegrationTest`, `MembershipAcceptanceIntegrationTest`,
`ConversationControllerIntegrationTest`, `ArticleControllerIntegrationTest`,
`ArticleUploadSizeLimitIntegrationTest`) rather than loosening
production behavior to match what the tests happened to assume.
**Applies to new decisions:** a CSRF exemption must always be a list of
exact pre-authentication paths, never a prefix/wildcard over a
controller namespace — a namespace groups routes by *resource*
(`/api/tenants/{tenantId}/...`), not by *authentication requirement*, so
a wildcard there will always eventually cover an authenticated,
state-changing endpoint nested under it. Any future controller placed
under `/api/tenants/**` (e.g. a support-channel controller) must not
assume it inherits CSRF exemption from this prefix — it doesn't, and
shouldn't.

### Frontend brand palette changes from "Ink and Signal" (navy+gold) to violet+white (2026-07-31)

The app owner explicitly decided to replace `knowly-app/`'s entire brand
color palette — previously a navy-blue `ink-*` scale for surfaces/text
plus a gold/amber `signal-*` accent scale, documented as "Ink and
Signal" in the PrimeNG-adoption entry above. This is a Tier 3 decision
(reverses a previously documented brand identity, applies globally, not
scoped to one screen) — recorded here as already made by the owner
after being asked to confirm scope (a full palette swap vs. just making
the login card more visually striking within the existing colors), not
decided unilaterally by an AI assistant. **Why:** the owner wants
violet/purple + white as the app's identity, with a lighter-violet
variant for light mode and a darker-violet variant for dark mode. **What
changed:** in `knowly-app/src/styles.css`, the `ink-*`/`signal-*`
Tailwind token *names* were kept (47 files reference them; renaming
would touch all of them for no functional gain) but their hex values
were repurposed from navy/amber to a violet scale — `ink-*` is now the
neutral violet surface/text scale, `signal-*` is now a more saturated
violet/purple accent scale reserved for primary actions/focus, matching
the original "accent, not decoration" role described in the file's
header comment (now updated to describe "Violet and Signal" instead of
"Ink and Signal"). All light/dark text-on-background pairings were
checked by hand for WCAG AA contrast (≥4.5:1) before landing. The login
screen (`login-page.component.ts`) was also given a more visually
distinct treatment as part of the same change — a gradient accent bar,
decorative blurred color blobs behind the card, and a richer
shadow/ring — while staying a single centered card (no split-panel) and
Tailwind-only, per this subproject's no-component-library convention.
**Applies to new decisions:** any new component or screen should assume
`ink-*` and `signal-*` now render as violet, not navy/gold — don't
hardcode navy/gold hex values assuming they match these tokens. A
pre-existing, unrelated bug was noticed during this work:
`knowly-app/src/app/shared/button-classes.ts` references
`dark:hover:bg-signal-950`, but the `signal-*` scale only goes up to
`900` — that class silently does nothing in dark mode and should be
fixed separately, not folded into this palette change.

## Tenant dashboard cards unified to the gradient-stat-card style (2026-07-31)

`gradient-stat-card.component.ts` was built for
`GlobalDashboardPageComponent` only; its own file comment noted that
restyling `metric-tile.component.ts` (the tenant-level `Painel`
dashboard's card) was "Out of scope" per
`specify/features/global-staff-dashboard-trends/SPEC.md`. The user
explicitly approved reopening that scope: `metric-tile.component.ts`,
`members-breakdown-card.component.ts`, `top-articles-table.component.ts`,
`message-split-chart.component.ts`, and
`conversations-activity-chart.component.ts` were all restyled to the
same `rounded-2xl ... bg-gradient-to-br from-ink-900 to-ink-950 ...
text-white` gradient-card chrome as `gradient-stat-card.component.ts`,
so both the staff global dashboard and the tenant dashboard share one
visual language for their metric cards. Only the presentational
container/text-color classes changed — each component's content
(loading/error/no-access states, data fetching, chart rendering) is
unchanged. Chart.js color options (`SPARKLINE_OPTIONS` in
`metric-tile.component.ts`, plus new options objects in
`message-split-chart.component.ts`/`conversations-activity-chart.component.ts`)
were also tuned, since their previous defaults were picked for a light
card background and read as low-contrast/invisible against the new dark
gradient. **Applies to new decisions:** any new tenant-dashboard card
component should default to this same gradient treatment rather than
the old plain `bg-white dark:bg-ink-900` card — that plain style is now
considered legacy/superseded on this dashboard, not a still-valid
alternative to pick between.

## `TenantFilterAspect` pointcut too broad: excluded Spring Data repository proxies with `!within(Repository+)` (Tier 2, 2026-08-01)

**Bug (live incident):** `TenantFilterAspect`'s pointcut was
`@Around("@annotation(org.springframework.transaction.annotation.Transactional)")`.
This also matched Spring Data JPA's own internal `@Transactional`
methods on `SimpleJpaRepository` (`findById`, `save`, ...), because
Spring's repository proxies are still ordinary Spring AOP proxies
subject to every other aspect registered in the application context —
they aren't exempt just because Spring Data built them. So any plain
repository call made from a thread with no active tenant in context
(e.g. a `@RabbitListener` background consumer, which is deliberately
*not* itself `@Transactional` — see `ArticleExtractionListener`,
`ArticleEmbeddingListener`) got the Hibernate `TenantFilter`
force-enabled with the fail-closed sentinel
(`TenantFilter.NO_ACTIVE_TENANT_SENTINEL`), silently hiding rows the
caller had every right to see by explicit id. In production this left
two uploaded articles permanently stuck in `PROCESSING`:
`ArticleExtractionListener.handle()` couldn't find its own row via
`articleRepository.findById(event.articleId())`.

**Fix:** narrow the pointcut to
`@annotation(org.springframework.transaction.annotation.Transactional) && !within(org.springframework.data.repository.Repository+)`.
`Repository` is a marker interface only Spring Data proxies implement;
every repository in this codebase is a plain
`interface X extends JpaRepository<...>` with no custom impl classes
(confirmed by appsec), so this exclusion is structurally airtight — it
cannot accidentally let a real tenant-scoped *service* method skip
filtering, since application services never implement `Repository`.

**Why Tier 2, not Tier 3:** this removes an accidental
exemption-widening bug — it makes the aspect fire in *fewer* cases
(never for repository-internal transactions), not more, and doesn't
loosen the tenant-isolation guarantee itself: every genuine
`@Transactional` service method is still covered exactly as before (see
`TenantFilterAspectPointcutIntegrationTest`'s
`genuineTransactionalServiceMethodStaysTenantFilteredWithNoActiveTenant`
regression guard). No new bypass surface is introduced, so this didn't
need product-owner sign-off — approved by software-architect + appsec.

**Test note:** the aspect firing for a `Repository`-typed target
doesn't reproduce through Spring Data's real proxy pipeline in a
`@SpringBootTest` in this Spring Boot version — matching a plain
`ArticleRepository.findById()` call against `TenantFilterAspect`
empirically never triggered the advice either before or after the fix
in a `@SpringBootTest` (see
`TenantFilterAspectPointcutIntegrationTest`, which is kept as a
behavioral regression guard even though it doesn't exercise the exact
before/after Red/Green transition). The actual pointcut-matching bug
was proven Red-then-Green with an isolated `AspectJProxyFactory` unit
test (`TenantFilterAspectPointcutUnitTest`) that directly builds a
woven proxy around a type implementing `Repository` with a
`@Transactional`-annotated method, matching `SimpleJpaRepository`'s
real shape without depending on Spring Data's own proxy-construction
internals for a given Spring/Spring Data version.

**Known follow-up (not in this fix's scope):** the two articles already
stuck in `PROCESSING` from before this fix landed were already consumed
off the queue and ACKed, so they will not automatically reprocess — they
need to be re-uploaded, or manually re-published, separately.

**Applies to new decisions:** any future aspect targeting
`@Transactional`-annotated methods generically (not a single named
service) must add the same `!within(Repository+)` exclusion (or scope
itself to a specific base package/annotation instead) — Spring Data
repository proxies are not automatically out of scope for
application-defined aspects just because they're framework-generated.

## `ArticleService.create()` deferred the "article uploaded" AMQP publish to `AFTER_COMMIT` (Tier 1, 2026-08-01)

**Bug (live incident, distinct from the `TenantFilterAspect` fix
above):** `ArticleService.create()` is `@Transactional` and called
`ArticleExtractionPublisher.publish(article.getId())` synchronously,
still inside its own open write transaction, before commit. On a fast
local broker `ArticleExtractionListener` could dequeue and call
`findById` before the INSERT was visible outside the transaction,
hitting its `article == null` branch and silently skipping processing
forever — same visible symptom as the tenant-filter bug
(`article.extraction_skipped ... reason=not_found`), different root
cause. Reproduced live: article id=4 hit this ~50ms after creation.

**Fix:** standard transactional-outbox-style deferral — `create()` now
raises a plain `ArticleUploadedApplicationEvent` via
`ApplicationEventPublisher` instead of calling the AMQP publisher
directly; a new `ArticleUploadedEventListener`
(`@TransactionalEventListener(phase = AFTER_COMMIT)`) is the only thing
that calls `ArticleExtractionPublisher.publish`, so the message can
never reach the broker before the row is committed and visible.
`ArticleExtractionListener` itself was checked and is *not* similarly
exposed — its class doc already establishes it's deliberately not
`@Transactional`, so its own `publishReadyForEmbedding` call isn't
wrapped in an open transaction the way `create()`'s was.

Routine, idiomatic Spring fix — no new tradeoff to weigh, so no
Tier 2/3 reasoning needed here.

**Known stuck rows (not in scope for this fix):** articles id 1, 2 (the
earlier tenant-filter bug) and id 4 (this race) are stuck in
`PROCESSING` in local dev — their AMQP messages were already taken off
the queue and ACKed, so they need to be re-uploaded or manually
re-published, same as noted in the entry above.

## `active-members-trend`: daily `@Scheduled` job over write-time upsert for the new active-member snapshot table (Tier 2, 2026-08-01)

The `active-members-trend` SPEC (backend) deliberately deferred the
"how" of maintaining a new go-forward-only `(tenant, UTC day) → active
member count` snapshot table to the PLAN, offering two candidate
mechanisms: a scheduled job, or an upsert triggered on every
`TenantMembership.active` write. This is the first time this codebase
introduces a scheduled job at all (`grep -rn "@Scheduled"
knowly-api/src/main/java` returned nothing before this decision), so the
choice is recorded here as precedent for the next "needs periodic
background computation" feature.

**Chosen: a daily `@Scheduled` job** (`ActiveMemberSnapshotScheduler`,
`cron = "0 5 0 * * *"`, UTC), not a write-time upsert. Two things ruled
out the write-time approach, even though it was the more "obvious"
option in a codebase with no scheduling precedent yet:

1. The SPEC's own NFR says the mechanism "must not add a per-request
   cost to any existing tenant-membership mutation path" — a write-time
   upsert runs synchronously inside `addMember`/`removeMember`'s request
   path, directly the thing that NFR was written to avoid.
2. More fundamentally, a write-time upsert only produces a row on a day
   where a membership actually changed state. On any day with zero
   membership churn for a tenant (the common case for a stable tenant),
   no row would be written — silently breaking the SPEC's REQ-2
   ("record exactly one snapshot row per tenant for that day"
   unconditionally, every day, not conditionally on activity). A
   scheduled job produces a correct row for every tenant every day,
   independent of whether anything changed, which is what "snapshot"
   actually means here.

**Idempotency (REQ-3, at-most-one-row-per-tenant-per-day) is handled by
a Postgres `INSERT ... ON CONFLICT (tenant_id, snapshot_date) DO
UPDATE` upsert**, not a `SELECT`-then-branch or an application-level
lock — a retried/duplicate job run for the same day is naturally
idempotent at the database level, no extra coordination needed.

**Accepted gap**: if the app is down at the scheduled run time, that
day's snapshot is permanently skipped (no catch-up-on-startup, no
backfill). This isn't a fresh product tradeoff needing sign-off — it's
the same "no backfill for missing history" position the SPEC's product
owner already confirmed for the feature's entire pre-rollout history,
just scoped down to a single missed day instead of the whole gap before
rollout.

**No new dependency**: `@Scheduled`/`@EnableScheduling` ship in
`spring-context`, already transitively present via
`spring-boot-starter` — only `@EnableScheduling` needed adding to
`KnowlyApplication`. Introducing the *first* scheduled job in this
codebase is still a real architectural decision (a new execution
context with no HTTP request/tenant context, its own failure mode if
the app is down at trigger time, and cron-schedule reasoning that
doesn't exist anywhere else here yet) — just not a Tier 3 one, since no
new artifact was added to `pom.xml`.

**Applies to new decisions:** the next feature needing "compute/record
something periodically, independent of any single request" should
default to a `@Scheduled` job in the owning package (not a write-time
hook scattered across mutation call sites) whenever the periodic
computation must happen unconditionally (i.e. its correctness doesn't
depend on "something changed recently") — and should use a
database-level `ON CONFLICT` upsert for idempotency over
application-level dedup logic wherever the target table has a natural
uniqueness key to upsert against, the same way this one does on
`(tenant_id, snapshot_date)`.

## `global-staff-dashboard-sparklines`: cumulative, carry-forward day-bucketed series is a new query/merge shape, computed as a single window-function query over full history rather than a period-bounded query

Every day-bucketed series in this codebase before this feature
(`ArticleRepository`/`ConversationRepository`/`MessageArticleCitationRepository`/
`TenantRepository.countTenantsByDay(Since)`) counts rows *created within*
the bucket, zero-filling any day with no inserts to `0`. This feature's
two new series (`totalTenantsPerDay`/`staffCountPerDay`, per
`global-staff-dashboard-sparklines/SPEC.md` REQ-1/2/3/4/5) are
cumulative running totals ("how many `Tenant`/staff-`User` rows existed
as of the end of day N"), which is a different shape: a quiet day must
**carry forward** the last known total, never reset to `0`, and the
value for any displayed day depends on **all** history up to that day,
not just rows inside the caller's requested display window.

**Decision: one native `@Query` per metric, computed over full history
regardless of `period`, using a window-function running sum over
day-bucketed counts** —

```sql
WITH daily AS (
  SELECT date_trunc('day', created_at AT TIME ZONE 'UTC')::date AS day, count(*) AS cnt
  FROM tenants GROUP BY day
)
SELECT day, sum(cnt) OVER (ORDER BY day) AS count
FROM daily ORDER BY day
```

— rather than (a) the existing two-query-variant pattern (`*Since(Instant)`
for bounded periods + bare `*()` for `period=all`), which would silently
compute the *wrong* running total for a bounded period (day 1 of a `7d`
window would show "new rows in the last 7 days" instead of the true
all-time running total as of that day), or (b) issuing one
`count(*) WHERE created_at <= :day` query per displayed calendar day,
which doesn't scale to `period=all` (unbounded day count) and turns a
90-day window into up to 90 round trips. The window-function query
aggregates once over `count(*) GROUP BY day` rows (cost bounded by
"distinct days with activity," same shape/cost as this codebase's
existing `period=all` queries), matching the SPEC's own NFR
("a single grouped aggregate query per metric ... no per-row loading").

A new app-layer merge helper (`mergeCarryForwardDays`, distinct from the
existing `mergeZeroCountDays`) then slices/carries this full-history
result into the caller's requested display range: for a bounded period,
the first displayed day seeds its carry value from the last cumulative
total recorded *before* the range starts (not `0`) — e.g. a tenant
created 6 months ago must still read `1` on day 1 of a `7d` window; for
`period=all`, the display range is `[earliest row's day, today]` (not
`MetricsPeriod.dateRange`, which has no concept of "start from the
data's own earliest day"), and an empty result set for `period=all`
correctly means "no rows ever" (REQ-5).

**Why the existing `DailyCountProjection` interface is reused as-is
(`getDay()`/`getCount()`) even though the value now means "cumulative
total" not "count created that day":** introducing a second, identically
shaped projection interface purely to rename `getCount()` would be a
distinction without a difference — every consumer of a day-bucketed
native `@Query` in this codebase already treats the projection as
schema-agnostic (`day`, some numeric value); the semantic difference is
carried by the SQL/column alias and the surrounding method name
(`countCumulativeTenantsByDay` vs. `countTenantsByDay`), not by the
projection type. A future third meaning for a day-bucketed numeric value
would still fit this same interface.

**Applies to new decisions:** before adding a new day-bucketed metric,
check whether it's "rows created per bucket" (use the existing
`*Since`/bare two-query-variant pattern + `mergeZeroCountDays`) or a
cumulative/running-total shape (use this single-query-over-full-history
pattern + `mergeCarryForwardDays`) — the two are not interchangeable,
and computing a cumulative metric with a period-bounded query is a
correctness bug (silently understates the true running total for any
window that doesn't start at "the beginning of time"), not just a style
choice. If a third day-bucketed shape shows up later that fits neither
merge helper, that's the trigger to reconsider whether these two helpers
should be unified behind a shared strategy, not before.

### First modal/confirmation-dialog pattern uses native `<dialog>`, not a hand-rolled overlay

`article-management` (frontend) needed a confirmation prompt before a
destructive action (REQ-11–13, deleting an article) — a check of the
whole `knowly-app/` codebase found no existing modal/overlay component
to reuse: `new-conversation-dialog.component.ts` is a route-level page
despite the name, not an overlay (no backdrop, no focus trap, no
`Escape` handling), and `members-page.component.ts`'s existing delete
action has no confirmation step at all. So this is genuinely the first
confirm/modal pattern in this codebase. **Decision:** build
`shared/confirm-dialog.component.ts` on the native HTML `<dialog>`
element (`showModal()`/`close()`), not a hand-rolled `position: fixed`
overlay + manual keydown listener + manual focus trap. **Why:** the
SPEC's own NFR requires the prompt be "keyboard-operable and
focus-trapped/dismissible with `Escape`" — `<dialog>` provides all
three natively (focus trap, `Escape`→`cancel` event, `::backdrop`) with
zero new dependency, whereas a hand-rolled overlay would have to
reimplement all three correctly (a common source of accessibility bugs)
or reach for a library, which would be a Tier 3 new-dependency decision
this feature doesn't need to make. This keeps the project's existing
"no component library, pure Tailwind + hand-rolled Angular components"
posture (see "Frontend drops PrimeNG..." below) intact — `<dialog>` is
a platform primitive, not a library.
**Applies to new decisions:** the next screen that needs a confirmation
prompt or any modal (a settings dialog, an "are you sure" for another
destructive action) reuses `shared/confirm-dialog.component.ts` — or,
if the shape doesn't fit (e.g. needs arbitrary content, not just a
message + confirm/cancel), extends the same native-`<dialog>`
foundation rather than introducing a second, different modal mechanism.
Reaching for a CDK overlay or a headless-UI/modal library instead of
`<dialog>` is a Tier 3 new-dependency decision — ask first, don't
assume the native element is insufficient without a concrete reason
(e.g. needing nested/stacked dialogs, which `<dialog>` doesn't handle
gracefully) written down.

## `deletion-confirmation-token`: `Accept-Language` locale resolution is a narrow, purpose-built parser, not Spring's `LocaleResolver`/`AcceptHeaderLocaleResolver`

`knowly-api` had no prior `Accept-Language`/`Locale` handling anywhere
in `src/main/java` (confirmed by inspection before writing this SPEC's
REQ-31). Rather than wiring up Spring's general-purpose
`LocaleResolver` machinery — which would happily resolve variants this
SPEC never wanted to support (e.g. `pt-PT`, or throw on a malformed
header) — this feature adds a small dedicated
`DeletionConfirmationLocaleResolver` that only ever answers the exact
binary question REQ-31 asks: does the header's highest-priority tag
match `pt`/`pt-BR`, or does everything else (including missing/
unparseable) fall back to EN. It parses defensively via
`Locale.LanguageRange.parse(...)`, is not registered as a Spring
`LocaleResolver` bean, and has no effect outside this one feature.
**Why:** general i18n negotiation and this SPEC's two-outcome locale
selection are different problems with different failure modes: a
general resolver optimizes for "pick the best match across many
supported locales," this feature needs "pick exactly one of two known
lists, defaulting safely on anything else." Reusing the general
machinery here would mean carrying its broader failure surface (locale
values this app doesn't actually support, resolver misconfiguration) to
solve a narrower problem, for the sake of "already existing" rather
than "actually fitting." **Applies to new decisions:** the next feature
that needs to read `Accept-Language` should reuse
`DeletionConfirmationLocaleResolver`'s parsing approach (defensive
`Locale.LanguageRange.parse`, explicit primary-tag check, explicit
default) if its locale set stays this narrow (EN/pt-BR, matching
`knowly-app`'s only two shipped UI locales per this SPEC's "Out of
scope"); only reach for Spring's full `LocaleResolver` machinery if a
future feature needs genuine multi-locale negotiation across more than
these two, and treat adding a third locale itself as a separate,
product-level (Tier 3) decision, not something to infer from this
resolver's shape.

## `deletion-confirmation-token`: a wrong-word validation attempt consumes the token, same as a correct one (Tier 3, user-confirmed 2026-08-01)

An appsec review of this feature's PLAN flagged that the original design
— consume the Redis token only on a correct match, leave it live on a
mismatch "so a mistyped attempt doesn't burn the real token" — left the
5-minute TTL window brute-forceable: a caller who already holds delete
permission on the resource (the token never grants authorization by
itself, only proves deliberate intent) could retry different two-word
guesses against the same live token for the full TTL, with only ~262k
combinations (512×511) per resource+user pair. That doesn't cross an
authorization boundary, but it defeats the specific guarantee this
mechanism exists to provide. No precedent existed for this exact
tradeoff in this codebase (`LoginCodeService`'s same non-burning-on-
mismatch behavior gates *authentication itself*, a materially different
threat model, so it wasn't treated as controlling precedent here) — this
was escalated to the user rather than decided unilaterally, per this
file's Tier 3 rule for genuine security tradeoffs. **Decision (user
choice): invalidate the token on the first attempt regardless of
match/mismatch.** A typo now costs the caller a fresh token request
(same UX as an already-expired token, reusing the existing refetch flow
— no new frontend code path), rather than nothing. **Applies to new
decisions:** any future single-use security-confirmation token in this
codebase (not just deletion) should default to consume-on-first-attempt
unless there's a specific reason (like `LoginCodeService`'s
authentication context) to tolerate mismatches — ask before choosing the
more lenient behavior rather than assuming it's fine by analogy to this
feature's original design.

## `deletion-confirmation-token` (frontend): `ConfirmDialogComponent` takes a function-typed `fetchToken` input rather than an output/event round-trip

Six different delete flows each need `ConfirmDialogComponent` to fetch a
confirmation word scoped to a different resource identity (an article
id; a `membershipId`; a `(membershipId, permission)` pair; a
`(membershipId, accessGroupId)` pair; and the staff-side equivalents of
the last two) the moment the dialog opens, without the caller having to
pre-fetch it before opening. No existing component in this codebase
takes a function-typed Angular `input()`. Two shapes were considered:
(a) the dialog emits an output when it opens (e.g. `tokenRequested`),
the parent listens, calls its own service method, and passes the result
back in via a `word` input; or (b) the parent passes a
`fetchToken: () => Observable<string>` closure once, and the dialog
calls it itself. **Decision:** (b). **Why:** (a) needs the parent to
also track "did I already respond to this open event" and reset that
per resource-instance-change, duplicated 6 times; (b) closes over the
resource identity once, at the call site, and lets the dialog own its
entire fetch/loading/error lifecycle internally as component-local
signals (consistent with "state lives in services as signals" — this
state isn't shared, so it stays local, same as `pendingDelete` already
does in `articles-page.component.ts`). A function-typed input is not a
new pattern/dependency, just an Angular `input()` typed as a callback —
Angular has always allowed this, this codebase just hadn't needed it
yet. **Applies to new decisions:** the next component that needs to
fetch something scoped to per-instance identity supplied by its parent,
where the parent has no reason to react to "the fetch happened" itself,
should default to a function-typed input over an output/input
round-trip — reserve the output/round-trip shape for cases where the
parent genuinely needs to intervene between "component wants data" and
"here's the data" (e.g. needs to show its own loading state, or combine
several async sources).

## `deletion-confirmation-token` (frontend): `Accept-Language` is set by a new interceptor sourced from `TranslocoService`, not left to the browser's default header

The backend resolves the deletion-confirmation word's language purely
from the raw `Accept-Language` request header (see the backend
feature's own PLAN.md). This app's actual displayed language, however,
is `TranslocoService`'s `activeLang`, driven by `language.service.ts`
and persisted in `localStorage` (`knowly.lang`) independently of the
browser/OS locale — a real, already-shipped case where a user's chosen
in-app language and their browser's default locale can differ. Letting
the browser send its own default `Accept-Language` (as every other
`/api/...` call implicitly does today, since nothing on the frontend has
ever needed to read that header before) would silently give such a user
a security word from the wrong language's wordlist. **Decision:** a new
`localeInterceptor` (`HttpInterceptorFn`, same shape as the existing
`authInterceptor`), registered in `app.config.ts`'s
`withInterceptors([...])`, reads `TranslocoService.getActiveLang()` and
sets `Accept-Language` explicitly on every outgoing request. **Why:**
this is the only point in the app that can see "what language is
actually being shown to this user right now" — doing this per-call-site
in each of the six new service methods would work but would have to be
repeated six times and would drift the moment a seventh caller forgets
it; an interceptor makes it structurally impossible to forget. **Applies
to new decisions:** any future backend endpoint that varies its response
by locale should keep relying on this same interceptor (already applied
to every `/api/...` call) rather than adding its own per-call header —
and if `knowly-app` ever ships a third UI language, updating
`localeInterceptor`'s mapping (currently a pass-through, since
`availableLangs` codes already match what the backend's locale resolver
expects) is a normal Tier 1 follow-on, not a new architectural decision,
since the interceptor itself doesn't hardcode the two-locale assumption
— the backend's own locale-resolver narrowness does (see the
"`Accept-Language` locale resolution is a narrow, purpose-built parser"
entry above).

## `member-admin-tenant-bypass` (frontend follow-up): gate nav items on `activeTenantRole() === 'MEMBER_ADMIN'` OR'd with the existing permission check, not by changing `GET /api/tenants/permissions`'s contract

The backend bypass (`PermissionAspect.checkPermission` unconditionally
passing for a `MEMBER_ADMIN` in their active tenant, shipped
2026-07-29) meant a `MEMBER_ADMIN` could already hit every tenant-scoped
endpoint (dashboard, articles, conversations, member management)
regardless of their `AccessGroup`/direct permission grants — but
`GET /api/tenants/permissions` still only returns explicitly-granted
permissions, so `nav-menu.component.ts`'s `PermissionsService.has(...)`
checks left such a `MEMBER_ADMIN` seeing almost no nav options. Two
ways to close this: (a) make the permissions endpoint itself return the
full permission set for a `MEMBER_ADMIN` caller, or (b) OR each nav
item's existing `.has(...)` check with `activeTenantRole() ===
'MEMBER_ADMIN'`, exactly the pattern `canSeeProfileEditRequests()` (in
the same component) already used for this same role. **Decision:**
option (b). **Why:** option (a) changes an existing, already-consumed
endpoint's contract/semantics (from "permissions explicitly granted" to
"permissions explicitly granted, or effectively-all-if-you're-a-role"),
which is a backend change with blast radius beyond this bug — any other
caller of that endpoint (present or future) would silently inherit a
different contract. Option (b) is purely additive on the one component
that actually needs the display-gating decision, costs nothing new
(the signal it reads, `ActiveTenantService.activeTenantRole()`, was
already being populated and read for the identical purpose one
computed away), and is the cheapest fix that doesn't touch backend
code at all. **Applies to new decisions:** when a backend permission
bypass exists for a specific role and a frontend display-gating check
needs to reflect it, prefer OR'ing the role check into the existing
gate at the point of use over widening a shared endpoint's contract —
reserve endpoint-contract changes for cases where multiple call sites
would otherwise need the same duplicated role-check logic.

## `member-admin-tenant-bypass` (backend consistency fix, 2026-08-01): supersedes the frontend-only OR-check above — make `GET /api/tenants/permissions` itself return the full permission set for `MEMBER_ADMIN`, mirroring `STAFF_ADMIN`

**Reverses the Tier 2 decision immediately above.** The user explicitly
flagged the asymmetry between the two "unrestricted role" bypasses:
`StaffController.ownPermissions` already special-cases `STAFF_ADMIN` to
return `List.of(GlobalPermission.values())` instead of computing
explicit grants, but `TenantController.ownPermissions` (via
`TenantService.ownEffectivePermissions`) had no analogous `MEMBER_ADMIN`
special-case, even though `PermissionAspect.checkPermission` (the
tenant-scoped equivalent of `GlobalPermissionAspect`) already
unconditionally bypasses for `MEMBER_ADMIN` in their active tenant.
**Decision:** `ownEffectivePermissions` should return
`List.of(Permission.values())` for a `MEMBER_ADMIN` of the active
tenant, exactly the shape of the pre-existing `staffAdmin` branch in the
same method. **Note on how this landed:** by the time this was picked
up, the concurrent `deletion-confirmation-token` feature's commit
`de2742d` had already touched this exact method and happened to include
the identical `MEMBER_ADMIN` branch as a side effect of its own
`TenantService.java` edits — so the source change shipped in `de2742d`,
not in a dedicated commit for this decision. This entry only added the
regression test
(`ownPermissionsReturnsTheFullPermissionSetForMemberAdminWithNoExplicitGrants`
in `TenantSessionIntegrationTest.java`) confirming the behavior, plus
this record of the decision and reasoning behind it. **Why the
reversal:** the previous entry's stated reason
for avoiding a backend change — "any other caller of that endpoint
(present or future) would silently inherit a different contract" — is
precisely what the user wants here: `STAFF_ADMIN` and `MEMBER_ADMIN` are
both "unrestricted role" bypasses and must resolve permissions through
the *same* mechanism (endpoint-level full-set special-case), not two
different mechanisms (one at the endpoint, one bolted onto a single
frontend component). Per explicit user instruction, consistency between
the two roles' permission-resolution mechanism outweighs the narrower
blast-radius argument. **Follow-up:** `nav-menu.component.ts`'s OR-check
(commit `18e505d`) is now redundant — the endpoint itself returns the
full set, so `.has(...)` alone is sufficient again — and is slated for
removal in a separate frontend follow-up (not done as part of this
backend fix). **Applies to new decisions:** when two roles are meant to
be symmetric "unrestricted" bypasses (one global, one tenant-scoped),
prefer keeping their permission-resolution *mechanism* identical across
both, even if that means a smaller, more targeted endpoint change than
the general "avoid widening a shared endpoint's contract" guidance would
otherwise suggest — symmetry between deliberately-parallel roles is a
stronger signal than the generic blast-radius concern.

## Permission granularity model reversed: edit/delete now require view; create stays independent (Tier 3, confirmed by the product owner 2026-08-02)

**What it was:** `tenancy` SPEC's original REQ-18 (and
`staff-rbac-split` SPEC's REQ-3, which mirrored it at the global/staff
scope) stated that permissions are **fully independent per action** —
"no permission implies any other... access is always exactly what was
explicitly granted, never inferred from ownership." Under that model, a
caller could hold, say, `ARTICLE_DELETE` without `ARTICLE_VIEW`, and the
system would have to honor that combination.

**What it is now:** the product owner explicitly confirmed (2026-08-02)
that this is reversed for edit and delete specifically, for **every**
CRUD-shaped resource in the system (tenants, articles, staff users,
tenant members, access groups, permission grants, and any future
resource with the same four actions) — not just one resource. The new
rule: view/list remains fully independent (as before); create remains
fully independent (as before); but **edit and delete each now
additionally require the caller to also hold view/list on that same
resource** — a caller cannot edit or delete something they cannot see.
Granting or revoking edit/delete does not itself auto-grant/auto-revoke
view/list; the dependency is enforced only at authorization-check time.

**Why:** the product owner's own words, paraphrased: "como a pessoa vai
deletar ou editar sem ver o recurso?" (how is the person going to
delete or edit a resource without seeing it?) — the fully-independent
model was judged operationally illogical for edit/delete specifically,
since there is no coherent way to act on a resource a caller cannot
locate/identify in the first place. View/list and create were
deliberately left alone: viewing something clearly needs nothing else,
and creating something clearly can't require having first viewed an
item that doesn't exist yet — the illogic argument only ever applied to
the "acting on an existing, specific item" cases (edit, delete).

**This is a genuine reversal, not a clarification** — see this file's
own incident record at the top (an AI once silently edited out an "Out
of scope" line and implemented it anyway) for why this distinction
matters: the previous "fully independent" wording is not being
reinterpreted here, it is being explicitly superseded, with the old
wording kept visible (struck through in spirit, not literally deleted)
in both `tenancy` SPEC's and `staff-rbac-split` SPEC's own Changelog
sections, each pointing to the new canonical source below rather than
silently rewriting history.

**Where the new rule lives:** a new SPEC,
`knowly-api/specify/features/permission-granularity-model/SPEC.md`, is
now the single canonical statement of this rule (REQ-1 through REQ-5),
plus a full per-resource gap analysis (REQ-6 through REQ-11) covering
every CRUD-shaped resource that exists today: `Article` (already has all
four permissions, just needs the new dependency wired into the
authorization check — no permission renaming needed); `Tenant` (today
only has `TENANT_CREATE` — `TENANT_VIEW`/`_EDIT`/`_DELETE` need to be
added, and the tenant edit/delete *business logic itself* doesn't exist
yet — deliberately scoped out of this SPEC into a future "tenant CRUD"
SPEC, since that's a new business capability, not just new permission
plumbing); staff users (today `STAFF_USER_CREATE`/`_VIEW` exist,
`_EDIT`/`_DELETE` need to be added — the deletion business logic itself
is `staff-rbac-management-operations` SPEC's, untouched here); tenant
members, access groups, and permission grants (today each is one
bundled `*_MANAGE_ANY` permission — each needs to become four/three
independent permissions with the view-dependency wired in, with a
migration that grants all of the new permissions to every existing
holder of the old bundled one, so no one's effective access silently
narrows at cutover). `tenancy` SPEC's REQ-18 and `staff-rbac-split`
SPEC's REQ-3 are both amended (not rewritten wholesale) to point at this
new SPEC as the source of truth, rather than duplicating the rule text
in three places that could drift independently.

**Applies to new decisions:** (1) any future CRUD-shaped resource added
to this system must, from day one, have its edit/delete permissions
depend on its view/list permission — this is now the house default, not
something to re-derive/re-confirm per feature. (2) A bundled
`*_MANAGE_ANY`-style permission covering multiple distinct actions on
one resource is no longer the preferred shape for a *new* resource going
forward (see `permission-granularity-model` SPEC's REQ-9/10/11 replacing
exactly this shape for tenant members/access groups/permission grants) —
default to one permission per action, with the view-dependency wired in
for edit/delete, rather than bundling. (3) Splitting a bundled
permission into granular ones for an *existing* resource must always
ship with a migration that grants every new granular permission to every
existing holder of the old bundled one, so effective access doesn't
silently narrow — the same "preserve effective access at cutover"
principle already established for the `GlobalRole` rename in
`role-model-refinement`.

## `tenant-creation` (frontend): the long-form staff screen adopts Reactive Forms + two extracted address/contacts components (Tier 2, 2026-08-02)

**What:** the `/tenants/new` screen (staff-only tenant + first-admin
creation) is the first form in `knowly-app` to use Angular **Reactive
Forms** (`FormGroup`/`FormBuilder`/`FormArray`), rather than the
plain-signal-plus-manual-validation convention every other form in this
codebase uses today (`members-page.component.ts`, the original
`tenant-create-page.component.ts` itself). It also introduces two new
standalone, presentational, reusable components:
`AddressFieldsComponent` (an 8-field structured-address fieldset bound
to a caller-supplied `FormGroup`) and `ContactsListEditorComponent` (a
repeatable contact-row editor bound to a caller-supplied `FormArray`).

**Why Reactive Forms here, specifically:** the amended SPEC
(`knowly-app/specify/features/tenant-creation/SPEC.md` REQ-7–REQ-21)
grew this form to ~25 fields across three sections, including a
repeatable contacts list and two independent structured-address
sub-sections, each with its own required/format validation rules
(REQ-8, REQ-9, REQ-10, REQ-13, REQ-14). The plain-signal convention this
codebase has used until now was viable for forms with a handful of flat
fields (`members-page.component.ts`'s invite form); reproducing
`FormArray`-equivalent add/remove-row behavior, per-field
touched/error/validator state, and nested-group scoping by hand for a
form this size would mean re-implementing most of what
`ReactiveFormsModule` already provides, with more surface area for bugs
than the framework module Angular already ships. `ReactiveFormsModule`
is part of `@angular/forms`, already a project dependency — this is a
new *pattern* adopted for one form, not a new package (no
`package.json` change), so it does not trigger the Tier 3 "new
dependency" bar.

**Why extracted, standalone `AddressFieldsComponent`/
`ContactsListEditorComponent`, not inline markup duplicated per
section:** the form needs the exact same 8-field address layout twice
(company address, first user's address — SPEC REQ-16 requires this
explicitly, "reuse presentation only, not data") and `user-profile-v2`
already independently solved "a repeatable contacts-row editor" inline
inside `ProfileFieldsFormComponent`. Rather than copy that inline
pattern a second time for `tenant-create`, this feature factors both
into standalone, purely presentational components taking a caller-owned
`FormGroup`/`FormArray` as input — no HTTP call, no service dependency,
no shared data between the two `AddressFieldsComponent` instances (each
is bound to an independent `FormGroup` instance, so filling one never
affects the other, satisfying REQ-16 by construction rather than by
convention).

**Known drift accepted, not resolved by this decision:**
`ProfileFieldsFormComponent`'s existing contacts editor stays inline,
un-refactored to use the new `ContactsListEditorComponent` — this
decision does not retroactively unify the two. If a third repeatable-
contacts consumer appears, reconciling all three into one shared
component becomes the stronger case; two independent implementations of
the same UI shape is accepted as a real but small amount of drift for
now, not silently ignored.

**Applies to new decisions:** (1) a new form in `knowly-app` that needs
`FormArray`-shaped repeatable rows, more than ~2 independent
nested-group sub-sections, or non-trivial cross-field validation should
default to Reactive Forms rather than the plain-signal convention — the
plain-signal convention remains the default for simple, flat forms (a
handful of top-level fields, no repeatable rows), consistent with
`members-page.component.ts`'s continued use of it. (2) A structured
"presentation shape reused across independent data" need (like this
feature's two addresses) should be factored into a standalone,
input/output-only component bound to a caller-owned form
construct — not duplicated markup, and not a shared data model.

## `tenant-creation`: tenant + first admin are created in one atomic call, not two (2026-08-02)

`tenant-creation/PLAN.md` (backend) originally kept `POST /api/tenants`
scoped to company fields + a bare `adminEmail`, leaving the first
admin's full profile (`mandatory-complete-profile`) and role
(`user-role-selection-at-creation`) to a *separate*, subsequent
`addMember` call — mirroring how every other member is added to an
already-existing tenant. Writing that PLAN surfaced a real contradiction
against the already-written frontend SPEC
(`knowly-app/specify/features/tenant-creation/SPEC.md` REQ-4/REQ-5),
which requires the `/tenants/new` screen to submit company data, the
first admin's full profile, and their role in **one** `POST
/api/tenants` call — not two round trips the UI would have to sequence
and partially roll back on failure.

**Decision:** `POST /api/tenants` becomes a single, atomic,
`@Transactional` endpoint that creates the `Tenant` **and** its first
`TenantMembership` (`User` + `UserProfile` + `Address` + `Contact` rows,
role defaulting to `MEMBER_ADMIN`) together. If any part fails —
invalid company fields, an incomplete first-user profile, an invalid
role — nothing is persisted: no orphaned tenant without a member, no
member without a tenant. **Why:** the frontend SPEC's one-call
requirement is the actual, already-approved product requirement here;
splitting it into "create tenant" then "add member" server-side would
force the frontend to either violate its own SPEC (two calls) or fake
atomicity client-side (call 1, then call 2, then manually clean up call
1's tenant if call 2 fails) — strictly worse than doing it properly in
one transaction, which this system already has the primitives for
(`@Transactional`, existing `UserProfileService` field-setting helpers).

This does **not** change `addMember` for the *n*-th member of an
already-existing tenant — that flow, and `mandatory-complete-profile`/
`user-role-selection-at-creation`'s field/role rules, are unchanged;
only *where* those already-decided rules apply for a brand-new tenant's
very first member moves from a follow-up `addMember` call to
`createTenant` itself, reusing the same lower-level persistence helpers
rather than re-deriving them.

**Applies to new decisions:** when a backend PLAN's natural
decomposition (one call per aggregate created) conflicts with an
already-approved frontend SPEC's UX requirement (one call for the whole
screen), the frontend SPEC's approved requirement wins if it was written
and approved first — don't silently keep the backend-only shape and
leave the frontend to reconcile it later. Cross-check a new SPEC's
"submits in one action" language against every backend endpoint it
implies before finalizing that backend's own PLAN.

## `tenant-creation`: tenant address lives as flat columns on `tenants`, not a separate 1:1 address table (2026-08-02)

`identity-profile-model-v2` split a user's address into its own
`addresses` table (PK = `user_id`) specifically because a user's address
is **optional and lazily created** — most users may never submit one,
and the row's lifecycle is independent from the rest of the profile.
`tenant-creation` SPEC's REQ-2 makes every tenant address sub-field
(except `complement`) **mandatory at creation** — a tenant's address row
would always exist, always be created in the same transaction as the
`Tenant` itself, and never have an independent lifecycle.

**Decision:** address fields (`postalCode`, `street`, `number`,
`complement`, `neighborhood`, `city`, `state`) are plain columns on
`tenants`, not a second `tenant_addresses` table. **Why:** a mandatory,
always-present 1:1 relation gains nothing from being split into a
separate table — no join is ever avoided (it's always fetched together
with the tenant), no optionality is modeled (there's nothing to be
"absent" the way a user's address can be), and splitting it would only
add a second entity/repository and an insert-ordering concern to every
tenant-creation code path for no benefit. `identity-profile-model-v2`'s
own underlying rule was never "always split address into its own
table" — it was "split out data whose lifecycle is genuinely
independent/optional from its parent." Applied consistently, that rule
produces the *opposite* answer for `tenants` than it did for `users`,
which is the correct outcome, not an inconsistency.

**Applies to new decisions:** before copying `identity-profile-model
-v2`'s `Address`-as-separate-table shape for a new entity's address (or
any other 1:1 sub-record), check whether that data is genuinely
optional/independently-lifecycled from its parent. If it's mandatory,
always created together with the parent, and never independently
edited on its own lifecycle, flat columns on the parent table are the
simpler, equally consistent choice — don't split it out just because a
superficially similar feature did.

## `tenant-creation`: first codebase precedent for a class-level custom Bean Validation `@Constraint` (conditional cross-field rule), diverging from `identity-profile-model-v2`'s "no custom `@Constraint`" call (2026-08-02)

`identity-profile-model-v2`'s `DECISIONS.md` entry chose a plain
service-layer `if` over a custom Bean Validation `@Constraint` for
`ContactType` format rules, reasoning that "exactly one conditional
rule doesn't justify introducing a whole new validation mechanism for a
one-off," and that case was validated deep inside a service method
called from multiple write paths (direct add, edit-request approval),
where a `@Constraint` would need duplicating or bypassing on one path
anyway.

`tenant-creation`'s `taxId`-format-depends-on-`country` rule (REQ-6) is
conditional in the same shape, but arrives at the opposite structural
answer: both fields arrive together in exactly **one** place
(`CreateTenantRequestDto` at `POST /api/tenants`, one write path, not
several). **Decision:** a class-level custom `@ValidTaxId` constraint
on `CreateTenantRequestDto`, applied via the controller's existing
`@Valid`. **Why:** this validates at the exact boundary Bean Validation
already owns for every other field on this DTO (`@Email`, `@NotBlank`),
producing the same uniform per-field 400 response REQ-3 requires,
without a service-layer `if` needing its own exception + handler just
to fold into that same field-level error list.

**Applies to new decisions:** `identity-profile-model-v2`'s "prefer a
plain service-layer conditional over a new `@Constraint`" guidance
applies when the validated data is reachable from multiple write paths
inside a service method — not universally. When a conditional/
cross-field rule's inputs arrive together in exactly one DTO at exactly
one endpoint, a class-level `@Constraint` is the better fit precisely
because Bean Validation already owns that boundary's error-response
shape; re-derive which precedent applies from *this* distinction, don't
default to whichever one shipped first.

### Concurrent "count must never reach a forbidden value" floors are enforced with a pessimistic row lock, not a read-then-write `COUNT`

`staff-rbac-management-operations/PLAN.md`'s "last admin" floor (never
let the count of `STAFF_ADMIN`/`MEMBER_ADMIN` reach zero via demotion or
deletion) is the first case in this codebase of a business rule that
must hold under concurrent requests, not just within a single
transaction. A plain `SELECT COUNT(*) ... WHERE role = ADMIN` read
followed by a conditional write, even inside `@Transactional`, does not
close this gap under this project's default (read-committed) isolation:
two concurrent demote/delete requests against two different "last
remaining" admins can each read "2 remain" before either commits, and
both proceed, landing on zero.

**Decision:** the floor check locks the actual candidate rows for update
(`@Lock(LockModeType.PESSIMISTIC_WRITE)` on a JPQL query returning the
matching rows, e.g. `UserRepository.findByGlobalRoleForUpdate(GlobalRole
role)`), counts the locked result set in Java, and only then proceeds
with the mutation — all inside the same transaction. A second concurrent
request against an overlapping row set blocks on the row lock until the
first transaction commits or rolls back, then re-reads a now-accurate
count. **Why:** this closes the TOCTOU window with the smallest possible
change — no new table, no advisory lock, no serializable-isolation
setting change for the whole app (which would affect unrelated queries)
— by locking exactly the rows whose count matters for this specific
check, for the shortest time that check needs them held.

**Applies to new decisions:** any future rule shaped like "a count of
some row set must never cross a threshold under concurrent mutation" —
not just role floors — should reach for this same pattern (lock the
candidate rows, count them locked, then mutate, all in one transaction)
rather than a bare `COUNT` read. Don't reach for `SERIALIZABLE`
isolation or an advisory lock as the default answer; those are broader
hammers than this project has needed so far.

## `tenant-creation` (backend): tenant + first admin are one atomic call, not two (Tier 3, decided 2026-08-02)

**What:** `POST /api/tenants` creates the `Tenant` **and** its first
`TenantMembership` (`User` + `UserProfile` + `Address` + `Contact`(s),
role defaulting to `MEMBER_ADMIN`) in a single request, inside one
`@Transactional` service method. If any part fails — invalid company
fields, an incomplete/invalid first-admin profile, a role the caller
isn't allowed to assign — the whole call fails and nothing is
persisted: no tenant without a member, no member without a tenant.

**Why:** a real contradiction was found between two already-written
artifacts in the same session — `knowly-app`'s `tenant-creation/SPEC.md`
REQ-4/REQ-5 (approved, requiring the `/tenants/new` screen to call
`POST /api/tenants` exactly once with company data, the first admin's
full profile, and their role together) versus `knowly-api`'s
`tenant-creation/PLAN.md` (which, before this decision, planned company
fields + a bare `adminEmail` on `POST /api/tenants`, deferring the
admin's full profile/role to a separate, subsequent `addMember` call).
Two separate calls creates a real inconsistent-state risk this product
cannot tolerate: a tenant could be created and then the second call
(profile/role) could fail for any reason (network, validation, a crash
between calls), leaving a tenant with zero members — permanently
inaccessible under the tenant-isolation model (`tenants` has no
"orphaned"/reconciliation path; a tenant with no `MEMBER_ADMIN` can
never be administered into a working state by anyone except staff
re-running the whole flow manually). A single atomic transaction makes
that state unreachable by construction rather than by discipline.

**Applies to new decisions:** any future multi-entity "create X and its
mandatory related Y in one user action" flow (this product's
established shape: `tenancy` REQ-10 already requires "a tenant is never
created without an admin") should default to one atomic transaction
behind one endpoint, not two client-orchestrated calls, whenever the
related entity is *mandatory* and *always* created together with the
first (not an optional, later-addable relation like `addMember`'s 2nd+
member case, which correctly remains its own call). Before splitting
a "create + its required child" flow across two endpoints, check
whether the child is truly optional/deferred (matches `addMember`'s
shape) or truly mandatory-at-creation (matches this decision) — the
same distinction `mandatory-complete-profile`'s "derived, not persisted"
entry already drew for pending state.

## `tenant-crud`: soft-deleted-tenant unreachability is enforced at the two existing tenant-selection chokepoints, not a new check (Tier 2, 2026-08-02)

`TenantService#requireActiveMembership` (a member's `switchActiveTenant`)
and `TenantService#requireTenant` (staff's "act as tenant") already are
the only two places any caller resolves "can I reach this tenant" before
a `TenantMembership`-less staff session or a real membership gets wired
into the security context — both already throw
`TenantAccessDeniedException` for "no access." Rather than adding a
third, separate "is this tenant soft-deleted" check somewhere else (a new
filter, a new guard, a new aspect), `tenant-crud` adds one condition —
`tenant.getDeletedAt() != null` — to those same two methods, throwing the
exact same exception. **Why:** this is the same "fails closed, no
parallel mechanism" principle `TenantFilter`/`TenantFilterAspect` already
establish for cross-tenant isolation, applied to a new axis (soft-deleted
vs. active) rather than a new mechanism for it — and because
`TenantController#switchActiveTenant` is already `@AuditLog`-annotated,
`AuditLogAspect`'s existing `TenantAccessDeniedException`/
`PermissionDeniedException` → `AuditOutcome.DENIED` special-case logs the
rejection automatically, satisfying the SPEC's "log it as a security
event" requirement with zero new audit code.

**Applies to new decisions:** before adding a new "can this caller reach
this tenant" check anywhere in the codebase, check whether
`requireActiveMembership`/`requireTenant` (or their eventual successors)
are already the funnel every session-establishing path goes through — if
so, extend those, don't add a parallel guard elsewhere; a soft-deleted
tenant's associated `TenantMembership` rows are also all
`active = false` (cascaded per `tenant-crud`'s own soft-delete step), so
any code path that already excludes inactive memberships (e.g.
`resolveSessionOutcome`'s `findByUserAndActiveTrue`) needs no additional
change — verify that before assuming a third check is needed.

### `staff-members-management-redesign` (frontend): shared list/table component configured via plain input objects, not projected templates (Tier 2, 2026-08-02)

`SharedListComponent`/`app-shared-list` (the reusable list layout REQ-1
of `staff-members-management-redesign` requires across staff directory,
tenant members, and future screens) takes its columns and row-actions as
plain data — `columns: input.required<SharedListColumn<T>[]>()` with a
`render(row: T)` function per column, and a similarly data-shaped
`rowActions` input — rather than Angular content projection
(`ng-content`/`ContentChild`/`TemplateRef`) for custom per-cell markup.

**Why:** this codebase has no existing generic "table with projected
cell templates" component to mirror; the closest existing precedent
(`TicketStatusBadgeComponent`'s color-map + i18n-key-map,
`buttonClass()`'s data-driven variant/state flags) is already
"data in, render function/map decides the output," not template
projection. Staying consistent with that shape keeps the component easy
to unit-test with Vitest (assert directly on the `columns`/`rowActions`
arrays and the rendered DOM, no `TemplateRef` mocking/`ViewContainerRef`
setup) and keeps sort/search/selection/pagination logic entirely inside
the shared component instead of leaking into each consumer's template.

**Applies to new decisions:** before reaching for Angular content
projection (`ng-content`, `ContentChild`, `TemplateRef` inputs) to make
a new shared component customizable, check whether the customization
can instead be expressed as data (a render function, a variant flag, a
map) the way `TicketStatusBadgeComponent`/`buttonClass()`/
`SharedListComponent` all do — this project's established pattern for
"reusable but per-screen-different" UI is data-driven configuration, not
template projection, and that should be the default unless a case
genuinely needs to project arbitrary markup a data-shaped input can't
express.

### Logical delete is now a standing, system-wide rule — no destructive operation may physically remove a row (Tier 3, user-confirmed 2026-08-04)

Found live: `StaffService#deleteStaffUser` called `userRepository.delete(user)`
directly and 500'd for any real staff account, since every staff user has at
least a mandatory `user_profiles` row (and virtually all have `audit_events`
rows from just logging in) and none of the 15 FK references onto `users.id`
have `ON DELETE CASCADE`. The first fix proposed was schema-level (`CASCADE`
on the tightly-owned tables, `SET NULL` on `audit_events.actor_user_id`). The
user redirected: **the fix belongs in policy, not schema** — nothing in this
system should ever be a physical delete. "Excluir" in every screen must mean
logical delete (a `deleted_at`/status marker), full stop.

**Rule, as given:** every destructive operation is logical. Additionally,
tightly-coupled owned resources cascade together: a `User` and its
`UserProfile`/`Address`/`Contact`s are one unit — if the user doesn't
effectively exist, neither does its profile data. A `Tenant` and its own
resources are the same relationship — deleting a tenant cascades to *its*
`Article`s and `Conversation`s (this explicitly supersedes `tenant-crud`
REQ-10's original "untouched by construction" scope decision). A pending
`ProfileEditRequest` whose target profile no longer effectively exists gets
cancelled, not left outstanding forever.

**What "every, absolutely every" turned out to mean in practice** (see
`git log` for the commit implementing this): the 8 JPA-level physical-delete
call sites existing at the time — `StaffService#deleteStaffUser`,
`TenantService#hardDeleteMember`, and the revoke/unassign paths for
`DirectGlobalPermissionGrant`/`UserGlobalAccessGroup` (global-scope) and
`DirectPermissionGrant`/`UserAccessGroup` (tenant-scope) — all converted to
set `deletedAt` instead of deleting. `ContactService#removeContact` (a ninth
site, not literally named "delete") converted the same way. `TenantMembership`
already had an `active` boolean for ordinary removal (`removeMember`) —
`deletedAt` is a **separate**, stronger marker for the hard-delete action
specifically, not a replacement for `active`; don't conflate the two when
touching membership code. `Tenant` (`deleted_at`, V25) and `Article` (`active`
flag) already had their own soft-delete before this — this decision's scope
was retrofitting the remaining hard-delete call sites to match, not
introducing the concept.

**Consequences that are easy to miss when adding a new grant/assignment
type:** (1) the unique constraint backing "one active grant per (subject,
permission)" must become a partial index (`WHERE deleted_at IS NULL`, see
migration `V28`), or re-granting a revoked permission collides; (2) the
grant/assign write path must reactivate a found-but-deleted row
(`grant.setDeletedAt(null)`) rather than blindly `orElseGet(() -> save(new
...))`, or a duplicate row accumulates; (3) **every** permission-resolution
read (`GlobalPermissionService`/`PermissionService`'s `effectivePermissions`)
and every listing/lookup read (staff user search, admin-floor-lock queries,
email/tax-id uniqueness checks) must filter `deletedAt IS NULL` explicitly —
this is security-critical, since a missed filter silently re-grants a
permission the caller believes was revoked, or lets a soft-deleted account's
email block a legitimate new signup.

**Login is a special case, not just another read filter:** `AuthController`
rejects a soft-deleted user's login outright (`InvalidCredentialsException`,
same as a wrong code/password) rather than falling into the "no such user
yet" `SelectionPending` branch — that branch still hands out a real,
authenticated zero-authority session, which a soft-deleted account must
never receive.

**Applies to new decisions:** any future hard/physical delete anywhere in
this codebase (a new entity, a new admin action, a bulk cleanup job) is
**out of bounds by default** — it needs a `deleted_at` (or equivalent status
marker) and a filtered read path from the moment it's designed, not added
later as a fix. If a genuinely new case seems to need physical deletion
(e.g. purging encrypted PII past a legal retention window), that is Tier 3 —
stop and ask, don't assume it's an exception to this rule.

### Local observability stack: grafana-lgtm + OTLP push, Logback bridge for logs (2026-08-04)

The app owner approved, as an already-cleared Tier 3 decision (new
external dependencies), a full local observability stack: Prometheus +
Loki + Tempo + Grafana for `knowly-api`. This entry records the choices
already made, same style as the PrimeNG entry above. **What was
discovered already in place, undocumented**, before any code changed
for this: `compose.yaml`'s `grafana-lgtm` service
(`grafana/otel-lgtm:0.29.2`, a single image bundling Grafana +
Prometheus + Loki + Tempo + an OTel Collector, `127.0.0.1:3000` for the
Grafana UI, `127.0.0.1:4317`/`4318` for OTLP ingest) and
`spring-boot-starter-opentelemetry` in `pom.xml` — real `knowly` metrics
and traces were already flowing to Prometheus/Tempo, confirmed live via
Grafana's own datasource proxy. **Why `grafana-lgtm` over separate
Prometheus/Loki/Tempo/Grafana containers**: it's a single image with
datasources and cross-links (Loki→Tempo trace-id derived field,
Prometheus exemplars→Tempo) already provisioned out of the box — no
hand-written provisioning YAML needed, less compose surface area to
maintain for a local-dev-only stack. **Why OTLP push (not Prometheus
pull-scrape) for metrics**: `spring-boot-starter-opentelemetry` already
defaults to pushing OTLP metrics/traces; adding a scrape config would be
a second, redundant path to the same data (`micrometer-registry-prometheus`
is present in `pom.xml` but currently unused/vestigial — not removed by
this decision, but don't build new pull-scrape config against it without
first checking whether OTLP push already covers the need). **Why a
Logback OTel appender for logs, not Promtail**: `knowly-api` isn't
containerized in local dev (`./mvnw spring-boot:run` against a
compose-only infra stack), so there's no container log stream for
Promtail to tail; a Logback appender
(`io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`,
version-pinned against `opentelemetry-api-incubator:1.62.0-alpha` to
match the SDK version Spring Boot's own dependency management fixes —
see `specify/features/observability-stack/PLAN.md` for the real
`NoClassDefFoundError` this mismatch caused) keeps every signal
(metrics, traces, logs) on the same OTLP push path, sharing the same
`OpenTelemetry` SDK instance and resource attributes. **Applies to new
decisions**: don't introduce a second metrics/logs/traces backend
(another Prometheus, another Loki, an OTel Collector config file) for
any future observability need in this repo — extend `grafana-lgtm`'s
usage (new dashboards, new log fields via `captureMdcAttributes`, new
spans) instead. This is a **local-dev-only** stack — no auth in front
of Grafana, no TLS, no retention tuning; do not treat it as
production-ready or extend it externally without a fresh Tier 3
conversation.

## `design-system-consistency-pass`: `SharedListComponent` gains an optional server-pagination mode, not a second component (Tier 2, 2026-08-05)

**Decision**: `SharedListComponent` (`knowly-app/src/app/shared/shared-list/`)
already served every in-memory-paginated listing screen in this app
(staff directory, tenant members). `select-tenant-page`'s staff-only
all-tenants fallback, and the new staff-user audit-trail page, are
server-paginated instead — the host fetches one page at a time from the
backend rather than holding every row in memory. Rather than forking a
second list component for that one behavioral difference, `shared-list.model.ts`
gained a `SharedListServerPagination { page; totalPages; totalElements }`
type and `shared-list.component.ts` gained a matching optional
`serverPagination` input plus `pageChange`/`searchChange`/`rowClick`
outputs. When `serverPagination()` is non-null, `visibleRows()`/`totalCount()`
skip the client-side filter/sort entirely and trust the host to have
already passed the current page's (already-filtered) rows; prev/next
controls render (hidden at `totalPages <= 1`, disabled at either
boundary) and emit `pageChange` instead of mutating any list-owned
state.

**Why extend the existing component instead of forking one**: columns,
row actions, search-input rendering, skeleton rows, and empty/error
states are identical between the two data-sourcing modes — only *how*
`visibleRows`/`totalCount` are computed and how a page change is
signaled differ. A second component would have meant keeping two
templates in lockstep for every future layout tweak (REQ-4's whole
point is one consistent list surface). This is genuinely new precedent
in this codebase — no existing component had "two data-sourcing modes
behind one input surface" before this — hence Tier 2 and recorded here
rather than left implicit in the diff.

**Follow-on `rowClick`**: needed independently once `select-tenant-page`
moved off its hand-rolled `<ul>`/`<button>`-per-row markup — row
selection (navigating into a tenant) has no equivalent in
`SharedListColumn`/`SharedListRowAction` (a *row action* is a distinct,
icon-scoped affordance, not "click anywhere on the row"). Rather than
solving this ad hoc per consumer, `rowClick = output<T>()` was added
once on the shared component (`<tr (click)="rowClick.emit(row)">`),
consumed today by `select-tenant-page` (row click selects a tenant) and
by nothing else yet, but generalized since `members-page`/
`staff-directory-page` had the same "click a row to open its detail
panel" shape before this pass moved their actions to explicit icons —
documented so a future add-back of row-click-to-open-detail doesn't
reinvent this.

**A necessary correction found during implementation, not itself part
of the PLAN**: row actions live *inside* the same `<tr>` that now has
`(click)="rowClick.emit(row)")`; without `$event.stopPropagation()` on
each row-action button's (and the row-selection checkbox's) own click
handler, clicking "delete" on a row would also fire `rowClick` for that
row (e.g. navigating away mid-delete-dialog-open on `select-tenant-page`).
Both handlers now stop propagation before/instead of bubbling to the
row's own click binding — noted here since it's a subtle
event-bubbling interaction the PLAN's text didn't spell out, not a
deviation from the PLAN's intent.

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
