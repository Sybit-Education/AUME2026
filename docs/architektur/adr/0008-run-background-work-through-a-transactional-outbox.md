# ADR-0008: Run background work through a transactional outbox

- Status: Proposed
- Created: 2026-10-29
- Decision owner: Integrationsteam
- Reviewers: Affected Lead Developers; responsible PO and Seezeit-IT as applicable
- Accepted by / date: Pending
- Supersedes: None
- Superseded by: None

## Context

Saving results and separately sending email can lose notifications or deliver them before commit. Reminders must survive restarts.

## Decision

Write notification intents into a PostgreSQL outbox in the domain transaction. Run a separate worker from the backend image. Use leased job claims, retry backoff, idempotency keys and failed-job visibility. Generate reports and execute retention jobs asynchronously where appropriate.

## Alternatives considered

Synchronous mail delivery couples inspection completion to SMTP availability. A broker introduces another service before its need is established.

## Consequences

Database polling adds load. Delivery is at least once; SMTP may duplicate messages after uncertain delivery, so do not promise exactly-once email.

## Validation and revisit triggers

Interrupt worker execution and verify retries without lost intents. Monitor queue age, failed jobs and duplicate domain-side effects.

## Basis and related records

Basis: [Sprint 0 artifact](../../anforderungen/fachlich/Artefakte_UserTeam_Sprint0.md), [quality scenarios](../../anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md) and [architecture overview](../Seezeit-Architektur.md). This record is a proposal, not evidence of implementation or approval.

See the [decision index](README.md) for related decisions.
