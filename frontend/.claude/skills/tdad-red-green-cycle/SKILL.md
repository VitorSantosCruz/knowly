---
name: tdad-red-green-cycle
description: Use for every implementation task in either repo — enforces test-first (Red before Green) and independent, trustworthy verification before a task is reported done. Triggers on "implementa", "corrige", or any task claiming to be finished.
---

# tdad-red-green-cycle

Enforces Test-Driven Agentic Development per `specify/memory/constitution.md`
and the Builder/Verifier reasoning in `../knowly/specify/memory/sdd-methodology.md`.

## Rules & anti-patterns

- **DO** write the test before the implementation, every time, and
  confirm it fails **for the right reason** (not a typo/compile error)
  before writing the fix.
- **STRICTLY PROHIBITED**: reporting a task as "done"/"passing" based on
  a command whose real exit code you didn't check directly. Piping a
  verification command through `| tail`/`| grep` and reading that exit
  code is not verification — it reads the pipe's last command's status,
  not the command that matters. Redirect to a file, check `$?`
  immediately: `cmd > file.log 2>&1; echo "EXIT:$?"`.
- **STRICTLY PROHIBITED**: trusting a background/agent-reported "exit
  code 0" without independently confirming it — a real incident on this
  project had exactly this: a background test run was reported green
  while containing a genuine, deterministic failure, because of the
  piped-exit-code issue above.
- **DO** re-run the specific test class in isolation after a shared
  code path changes (a guard, an aspect, a service method with multiple
  callers) — don't assume the one test that motivated the change is the
  only one affected.
- **DO**, before marking a *feature* (not just a task) done, run the
  Analyze gate: re-check every SPEC.md acceptance-criterion checkbox
  against the finished implementation, one by one.

## Execution steps

1. Read the SPEC requirement (REQ-N) this task implements.
2. Write the failing test. Run it. Confirm it fails on the actual
   assertion, not a setup error.
3. Write the minimum code to pass it.
4. Run the test again — file output redirected, exit code checked
   directly. Confirm Green.
5. Run the full relevant suite (not just this test) the same
   exit-code-safe way, at least once, before considering the task done.
6. Commit (per this repo's commit-as-you-go convention).
7. At feature completion: run the Analyze gate (see
   `sdd-methodology.md`) before updating `PROJECT_STATUS.md`.

## Template — trustworthy verification command

```bash
# Backend
./mvnw -q -o test > /tmp/verify.log 2>&1; echo "EXIT:$?"
tail -100 /tmp/verify.log   # read separately, never piped into the exit-code check

# Frontend
npx ng test --watch=false > /tmp/fetest.log 2>&1; echo "EXIT:$?"
```

If a full run must go in the background (long-running suite), still
redirect to a file and echo the real exit code as the last statement in
that same background command — never rely on a wrapping tool's own
"completed" status as a stand-in for the command's actual exit code.
