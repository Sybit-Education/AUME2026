# ADR-0001: Record decisions as Markdown in Git

- Status: Accepted
- Created: 2026-10-29
- Decision owner: Project architecture team
- Reviewers: Project architecture team
- Accepted by / date: 2026-10-29
- Supersedes: None
- Superseded by: None

## Context

Architecture decisions need a durable history accessible to developers and reviewers. Also see: https://adr.github.io

## Decision

Store numbered Markdown ADRs in coreteam/architektur/adr beside the application source. Review changes through pull requests.

## Alternatives considered

A wiki is easy to edit but separates decisions from code reviews. A dedicated ADR service adds hosting and maintenance.

## Consequences

Readable without special tools and versioned alongside code. The team must maintain links and index entries.

## Validation and revisit triggers

A reviewer can follow every relative link; each accepted decision has a named approver and date.

## Basis and related records

Basis: Sprint 0 artifact and architecture proposal in the current conversation. Technology choices are recommendations, not implemented or team-approved facts.

See the [decision index](README.md) for related decisions.
