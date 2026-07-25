# SPEC — <feature name>

> The what and the why. No technical implementation details.

## Context and motivation

<Why this feature exists. What business problem it solves.>

## User stories

- As a `<role>`, I want to `<action>` so that `<benefit>`.

## Requirements (EARS/GEARS)

Use exactly one of the patterns below per requirement. Number the
requirements.

- **[Ubiquitous]** The `<system>` shall `<action/property>`.
- **[Event-Driven]** When `<trigger>`, the `<system>` shall `<action>`.
- **[State-Driven]** While `<state>`, the `<system>` shall `<action>`.
- **[Optional Feature]** Where `<feature/config>`, the `<system>` shall
  `<action>`.
- **[Unwanted Behavior]** If `<error condition>`, then the `<system>` shall
  `<action>`.
- **[Complex]** Where `<config>`, while `<state>`, when `<trigger>`, the
  `<system>` shall `<action>`.

## Non-functional requirements

- Security: <e.g. authentication, authorization, encryption>
- Performance/SLA: <e.g. max latency, throughput>
- Observability: <e.g. metrics, logs, traces>

## Acceptance criteria

- [ ] <verifiable criterion 1>
- [ ] <verifiable criterion 2>

## Out of scope

- <what is explicitly not being done in this feature>
