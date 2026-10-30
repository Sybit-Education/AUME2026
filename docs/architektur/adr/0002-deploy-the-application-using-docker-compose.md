# ADR-0002: Deploy the application using Docker Compose

- Status: Accepted
- Created: 2026-10-29
- Decision owner: Project architecture team
- Reviewers: Project architecture team
- Accepted by / date: 2026-10-29
- Supersedes: None
- Superseded by: None

## Context

The customer (Seezeit) requires the whole application to be hosted in Docker Compose. The expected scale is 600 WGs across 16 residences.

## Decision

Use a dedicated Linux host and one production Compose project for proxy, frontend, API, worker, PostgreSQL, identity provider and mail relay. External DNS, browsers, certificate authorities and mail delivery are environmental dependencies.

## Alternatives considered

Kubernetes provides orchestration but adds operational complexity. Managed application services do not meet the stated deployment constraint.

## Consequences

One host is a single point of failure. Backups must leave the host. Scale and availability requirements may require revisiting this decision.

## Validation and revisit triggers

Inventory every runtime service; verify only intended ingress is published. Agree recovery targets and perform a host-loss restore exercise.

## Basis and related records

Basis: Sprint 0 artifact and architecture proposal in the current conversation. Technology choices are recommendations, not implemented or team-approved facts.

See the [decision index](README.md) for related decisions.
