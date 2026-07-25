---
name: ci-pipeline-guard
description: Use before editing any .github/workflows/*.yml, compose.yaml, or Dockerfile in either repo, or when reporting any CI/test-run result as pass/fail. Triggers on "muda o pipeline", "adiciona um workflow", "roda os testes e me fala se passou".
---

# ci-pipeline-guard

Pre-flight checklist for CI/Docker/compose changes, and the mandatory
trustworthy-verification pattern for reporting any pass/fail result on
**knowly**/**knowly-app**.

## Rules & anti-patterns

- **STRICTLY PROHIBITED**: reporting a verification result based on a
  piped command's exit code (`cmd | tail -N; echo $?` reports `tail`'s
  status, not `cmd`'s, since `pipefail` isn't set in this environment).
  This already caused a real incident: a background test run with a
  genuine failure was reported green for a full round of this project's
  own development. Always redirect to a file and check `$?` immediately
  after the command that matters, as the very next statement.
- **STRICTLY PROHIBITED**: relaxing `reuseForks=false` or
  `spring.test.context.cache.maxSize=1` in `pom.xml`'s Surefire config
  without re-running the same multi-round A/B this project already did
  (documented in `DECISIONS.md`) — these settings prevent a
  live-observed class of flakiness (shared Redis counters, DB/context
  collisions), not a theoretical one. `forkCount` is a safer, separate
  knob to tune, but still needs ≥2 clean full-suite runs (trustworthy
  exit code, per above) before trusting a change.
- **DO** keep the deploy target as a plain Docker image → GHCR — no
  Kubernetes manifests without flagging it as a new-dependency Tier 3
  decision first.
- **DO** keep `compose.yaml` hardened: no hardcoded secrets
  (`${VAR:?...}` + `.env` — this Compose-file syntax is *correct* here,
  unlike inside Spring's `application.yaml`, see `DECISIONS.md`),
  `cap_drop: ALL` + minimal added capabilities, `127.0.0.1`-bound ports,
  real healthchecks, resource limits, named volumes only.
- **DO** verify a "the app won't start without this secret" claim with
  an actual unset-variable run before trusting it — this exact
  assumption failed once already in this project (a `${VAR:?message}`
  in `application.yaml`, not `compose.yaml`, silently let a missing
  secret through instead of failing).
- **DO** check CodeQL's and Dependabot's actual findings, not just
  whether their jobs went green — a passing job with unread findings is
  not the same as a reviewed one.

## Execution steps (workflow/compose/Dockerfile change)

1. Read the existing file fully before editing — don't assume its
   current behavior from the filename.
2. If touching Surefire config: read `DECISIONS.md`'s Surefire entry
   first, run the A/B protocol (≥2 trustworthy-exit-code full runs per
   candidate setting) before proposing a change.
3. If touching secrets/env handling: verify the fail-closed behavior
   empirically (unset the var, confirm the actual failure).
4. Make the change, run the affected pipeline locally if possible
   (`act`, or the equivalent job's individual steps), or push to a
   branch and watch the real GitHub Actions run — not just "should work."

## Execution steps (reporting any test/build result)

1. Run the command with output redirected to a file, exit code checked
   as the literal next statement: `cmd > /tmp/out.log 2>&1; echo "EXIT:$?"`.
2. Read the exit code value directly — don't infer it from a
   surrounding tool's "completed" status.
3. If reporting a background run's result, re-derive the exit code from
   that same file/echo pattern — never from the background-task
   notification's own success framing alone.
4. State the actual number in your own report ("REAL_EXIT:0, confirmed")
   rather than "it passed" — the number is the evidence, the paraphrase
   is not.

## Template

```bash
./mvnw -q -o test > /tmp/verify.log 2>&1; echo "EXIT:$?"
# then, separately:
tail -100 /tmp/verify.log
```
