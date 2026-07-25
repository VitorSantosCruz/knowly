---
name: owasp-sanitization-check
description: Use before merging any change touching authentication, authorization, input handling, PII, secrets, or CSRF configuration in either repo. Triggers on "novo endpoint de login", "campo novo com dado sensível", "mudança no SecurityConfig".
---

# owasp-sanitization-check

Per-endpoint/per-input security review checklist for **knowly**, mapped
to this project's actual established security posture (not a generic
OWASP restatement).

## Rules & anti-patterns

- **STRICTLY PROHIBITED**: adding a new authenticated endpoint to
  `SecurityConfig`'s CSRF `ignoringRequestMatchers`. That list is only
  for endpoints provably reachable *before* authentication exists. This
  is Tier 3 — stop and ask, don't approve it in review either.
- **STRICTLY PROHIBITED**: keying brute-force lockout by IP. Key by
  account identifier. Enumeration protection is CAPTCHA-on-velocity, not
  a per-identifier counter (each distinct guess only happens once, so a
  counter never trips for enumeration specifically).
- **DO** verify a verify-endpoint always performs its expensive
  comparison (hash check) — against a real hash or a constant dummy hash
  — so timing never reveals existence. See `OneTimePasswordService`'s
  `dummyHash` pattern.
- **DO** run raw/native queries only with parameter binding — check for
  string concatenation into SQL first.
- **DO** confirm every new PII field (email, and especially anything
  like a future CPF/RG/phone number) is masked in logs
  (`PiiMasker#maskEmail` or an equivalent) and has an explicit
  retention/access-control decision on record — sensitive personal data
  is Tier 3, don't default the retention policy yourself.
- **DO** verify a "required, fails if missing" mechanism actually
  throws for the runtime in play — don't trust syntax that merely looks
  defensive (`${VAR:?message}` is Compose/shell syntax; Spring silently
  treats it as a literal default and lets a missing secret through).
  A quick standalone test settles it either way.
- **DO** confirm a new tenant-owned entity is scoped by the existing
  Hibernate `@Filter`, and that staff bypass (via
  `@RequiresPermission`/`@RequiresGlobalPermission`'s `STAFF_ADMIN`
  short-circuit) never also bypasses that filter — authorization bypass
  and isolation bypass are different guarantees; only the first is
  meant to exist for staff.

## Execution steps

1. Identify what's new: an endpoint? A field? A config change?
2. Endpoint: confirm CSRF handling is the default (not exempted) unless
   provably pre-auth. Confirm the permission annotation exists and maps
   to the right `Permission`/`GlobalPermission`. Confirm input DTOs use
   Jakarta Validation, not manual checks.
3. Field: confirm PII masking in logs, confirm a uniqueness constraint
   is a real DB constraint if the business rule requires it, confirm
   retention/encryption has been explicitly decided (not defaulted) for
   anything sensitive.
4. Config: confirm no new Actuator exposure, no new CORS origin, no
   relaxation of the `reuseForks`/context-cache Surefire isolation
   settings, without a Tier 3 stop-and-ask first.
5. Run CodeQL locally if the change is substantial
   (`gh workflow run codeql.yml` or wait for the PR's own run) — read
   the actual findings, don't just check the job went green.
6. Check Dependabot's open PRs for this dependency area before adding a
   new one manually — it may already be tracked.

## Template — timing-safe verify pattern

```java
boolean valid = user != null && hasValidSecret(user);
String hashToCheck = valid ? user.getSecretHash() : dummyHash;
boolean matches = passwordEncoder.matches(input, hashToCheck); // always runs
if (!valid || !matches) {
    throw new InvalidCredentialsException();
}
```
