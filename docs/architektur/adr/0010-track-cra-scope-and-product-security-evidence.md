# ADR-0010: Track CRA scope and product security evidence

- Status: Proposed
- Created: 2026-10-29
- Decision owner: Project architecture owner (unassigned)
- Reviewers: Core Team; Seezeit product/operations representatives as applicable
- Accepted by / date: Pending
- Supersedes: None
- Superseded by: None

## Context

The prior architecture recommendation identified possible EU Cyber Resilience Act obligations. Applicability depends on supply model and product boundary; Docker alone does not settle scope.

## Decision

Maintain a CRA applicability assessment, manufacturer/maintainer ownership, threat model, SBOM, vulnerability disclosure process, release security evidence and support plan. Treat legal conclusions as pending qualified review. Track GDPR separately. Use the sources in the CRA checklist as starting references, not a declaration of conformity.

## Alternatives considered

Assuming all browser applications are in scope or that all student projects are exempt can produce incorrect obligations. Deferring security until classification is complete creates avoidable risk.

## Consequences

Security engineering proceeds now; legal duties and conformity steps remain conditional on scope. Funding and ownership after the student project must be resolved.

## Validation and revisit triggers

Approve a documented supply-model assessment and name the maintenance owner before production handover. Reassess on redistribution, native client delivery or commercialization.

## Basis and related records

Basis: Sprint 0 artifact and architecture proposal in the current conversation. Technology choices are recommendations, not implemented or team-approved facts.

See the [decision index](README.md) for related decisions.
