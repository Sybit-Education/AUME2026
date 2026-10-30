# ADR-0003: Structure the backend as a modular monolith

- Status: Accepted
- Created: 2026-10-29
- Decision owner: Project architecture team
- Reviewers: Project architecture team
- Accepted by / date: 2026-10-29
- Supersedes: None
- Superseded by: None

## Context

The product has related transactional workflows and a small initial deployment. Independent scaling is not demonstrated.

## Decision

Use a Spring Boot backend with modules for identity/housing, standards, planning, inspections, results/follow-ups, WG organization, notifications and audit/reporting. Expose REST endpoints; enforce module boundaries through internal interfaces.

## Alternatives considered

Microservices add distributed transactions and deployment overhead. An unstructured monolith obscures ownership and privacy boundaries.

## Consequences

One backend release is simpler to operate. Module isolation must be maintained in code review; shared deployment limits independent scaling.

## Validation and revisit triggers

Review dependency direction and prohibit direct writes to another module’s owned data. Revisit when independent team ownership or measured scaling pressure emerges.

## Basis and related records

Basis: Sprint 0 artifact and architecture proposal in the current conversation. Technology choices are recommendations, not implemented or team-approved facts.

See the [decision index](README.md) for related decisions.
