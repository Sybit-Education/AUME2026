# ADR-0007: Version standards and preserve published inspection history

- Status: Proposed
- Created: 2026-10-29
- Decision owner: Integrationsteam
- Reviewers: Affected Lead Developers; responsible PO and Seezeit-IT as applicable
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

Basis: [Sprint 0 artifact](../../anforderungen/fachlich/Artefakte_UserTeam_Sprint0.md), [quality scenarios](../../anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md) and [architecture overview](../Seezeit-Architektur.md). This record is a proposal, not evidence of implementation or approval.

See the [decision index](README.md) for related decisions.
