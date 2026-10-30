# ADR-0007: Version standards and preserve published inspection history

- Status: Proposed
- Created: 2026-10-29
- Decision owner: Project architecture owner (unassigned)
- Reviewers: Core Team; Seezeit product/operations representatives as applicable
- Accepted by / date: Pending
- Supersedes: None
- Superseded by: None

## Context

US-24 allows criteria maintenance; US-34 requires the inspector to decide the overall result. Historical records must stay understandable.

## Decision

Bind each inspection to an immutable checklist version. Model explicit draft, finalized and published transitions. The inspector explicitly selects the overall result. Published corrections create attributable revisions. Follow-up inspections link to the original inspection and unresolved defects.

## Alternatives considered

Referencing a mutable current checklist changes historical meaning. Automatically deriving the final result conflicts with US-34.

## Consequences

Historical snapshots consume storage but provide reproducible reports. Transition permissions and revision semantics require explicit business rules.

## Validation and revisit triggers

Change the current checklist and demonstrate unchanged historical reports. Test authorized transitions, concurrent edits and correction attribution.

## Basis and related records

Basis: Sprint 0 artifact and architecture proposal in the current conversation. Technology choices are recommendations, not implemented or team-approved facts.

See the [decision index](README.md) for related decisions.
