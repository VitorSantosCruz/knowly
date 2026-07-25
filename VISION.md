# Vision — what knowly is for

> This is a **living document**, not a spec and not a historical log.
> `PROJECT_STATUS.md` tracks what's built; this file tracks *why* — the
> product this codebase is building toward. Any AI assistant reading this
> repo should read this file to understand intent, then feel free to
> reason beyond what's explicitly written here — the plan can and should
> evolve past what's captured today. If the user describes a new
> direction, ambition, or priority in conversation, **update this file**
> instead of letting that context live only in a chat transcript that
> disappears when the conversation ends.

## What knowly is

knowly is an **internal knowledge base with an AI layer on top**, built
for companies that already have a pile of documentation, articles, and
institutional knowledge scattered across people's heads and drives — and
want their team to be able to *ask questions and get answered from that
content directly*, instead of hunting through it manually.

Concretely, today, that means:

- A company (**tenant**) uploads its own material — text, images (OCR'd
  through tesseract), PDFs — as **articles**.
- Those articles get embedded (pgvector) so they're semantically
  searchable, not just keyword-matched.
- Employees **chat** with an AI assistant that answers grounded in that
  tenant's own articles, with **citations** back to the source article —
  not a generic LLM answering from thin air.
- Everything is **permission-gated**: who can view, upload, edit, delete
  articles, and who can even see a given conversation, is governed by a
  real access-control model (direct grants + reusable access groups), not
  an afterthought bolted on later.
- Usage is visible via **dashboard metrics** (which articles get cited,
  how much the assistant is actually used) — the product should be able
  to show a customer that it's earning its keep.

## Who it's for

**B2B: companies that need an internal knowledge base with AI**, not
individual consumers and not (at least for now) a horizontal
build-your-own-chatbot platform. The buyer is an organization with
existing internal knowledge that's hard to search; the user is their
employees, asking questions in natural language instead of digging
through folders or asking a coworker who might be on vacation.

## Why the architecture looks the way it does

Some technical decisions in this repo are direct consequences of that
product goal, not arbitrary engineering taste — worth knowing so they
aren't "simplified away" by someone who doesn't see the connection:

- **Multi-tenancy is load-bearing, not incidental.** Each customer
  company is a tenant with fully isolated data (Hibernate-filter-enforced,
  fails closed). This is the actual product boundary: one company's
  articles must never leak into another's chat answers.
- **Staff (ConectaByte's own team) can act as any tenant without holding
  a membership.** This exists because *we* are the ones onboarding and
  supporting each customer — someone at ConectaByte needs to be able to
  go look at "Acme Corp's" tenant to debug or help set it up, without
  every tenant needing to manually invite a support account. This was
  built reactively (a real staff account hit a live bug from having zero
  memberships) but reflects a real, ongoing need: the platform is
  operated *for* tenants by ConectaByte staff, not purely self-serve.
- **Permissions are granular and access groups are reusable** because a
  real company's internal structure isn't flat — who can upload vs. who
  can only ask questions vs. who administers the tenant is a distinction
  customers will actually want to draw, especially as a tenant's article
  library becomes a real internal asset worth protecting.
- **Everything is audited** (every read and write) because in a product
  where an AI answers questions grounded in company-internal documents,
  "who saw what, and when" is the kind of trust/compliance question that
  will come up eventually — better to have it from day one than retrofit
  it once a customer asks.
- **Citations are a first-class part of the chat response**, not a nice
  extra — the whole value proposition over "just use ChatGPT" is that the
  answer is traceable to a specific internal document. If the assistant
  ever answers without being able to point to where it got that from,
  the product has failed at its actual differentiator.

## What's deliberately not decided yet

- Billing/plan differentiation per tenant — explicitly out of scope in
  the `tenancy` SPEC so far; will matter once there's a real go-to-market
  motion, not before.
- Self-service tenant signup — today tenants are staff-provisioned only.
  That's consistent with an early, high-touch-onboarding phase of a B2B
  product; may or may not stay that way as the customer base grows.
- Any cross-tenant analytics/benchmarking product ("how does our usage
  compare to similar companies") — nothing here suggests this yet, but
  the dashboard-metrics foundation (`MessageArticleCitation`) is the kind
  of data that could eventually support it, if that direction comes up.

## How to use this file

If you're an AI assistant starting fresh work here: read this, understand
that the north star is "AI-answered, cited, permission-respecting search
over a company's own internal knowledge," and let that inform judgment
calls the SPEC/PLAN/TASKS process doesn't spell out explicitly (e.g. when
choosing between two reasonable technical approaches, prefer the one that
keeps tenant isolation and citation-traceability airtight over one that's
marginally simpler). If the user tells you something that changes or adds
to this vision, fold it into this document in your own words — don't ask
them to repeat it in some future conversation.
