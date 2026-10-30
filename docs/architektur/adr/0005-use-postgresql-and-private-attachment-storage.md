# ADR-0005: Use PostgreSQL and private attachment storage

- Status: Proposed
- Created: 2026-10-29
- Decision owner: Project architecture owner (unassigned)
- Reviewers: Core Team; Seezeit product/operations representatives as applicable
- Accepted by / date: Pending
- Supersedes: None
- Superseded by: None

## Context

Inspections, resident memberships and follow-ups require structured relationships. Photos and reports need controlled access.

## Decision

Use PostgreSQL for domain records and a private filesystem volume for attachments. Access attachments only through authorization-checked backend endpoints. Store metadata and opaque storage identifiers in PostgreSQL. Use a storage abstraction for later object storage.

## Alternatives considered

Public file serving risks bypassing authorization. Database blobs simplify atomicity but enlarge database backups.

## Consequences

Database and file backups require consistency. Attachment creation/deletion needs staging, retry and orphan cleanup; database and filesystem changes are not one transaction.

## Validation and revisit triggers

Test unauthorized attachment retrieval, upload limits and restoration of a matching database/file backup set.

## Basis and related records

Basis: Sprint 0 artifact and architecture proposal in the current conversation. Technology choices are recommendations, not implemented or team-approved facts.

See the [decision index](README.md) for related decisions.
