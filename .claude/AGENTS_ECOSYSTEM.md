# Agent ecosystem — knowly-app

The full agent/skill topology and orchestration flow for this repo pair
lives in the backend repo:
[`../knowly/.claude/AGENTS_ECOSYSTEM.md`](../../knowly/.claude/AGENTS_ECOSYSTEM.md)
— same reason `VISION.md`/`DECISIONS.md` live there too (one canonical
copy, referenced from both repos).

This repo's own `.claude/agents/` and `.claude/skills/` contain: every
frontend-only agent/skill (not duplicated — Angular/Tailwind-specific),
plus a **duplicated, identical-in-intent** copy of every agent/skill
that genuinely spans both repos (PO, Software Architect, QA, AppSec,
DevOps/SRE and their skills) — see the table in the canonical file
above for exactly which is which.
