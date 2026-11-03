# ADR-0010: Track CRA scope and product security evidence

- Status: Proposed
- Created: 2026-10-29
- Decision owner: Integrationsteam
- Reviewers: Affected Lead Developers; responsible PO and Seezeit-IT as applicable
- Accepted by / date: Pending
- Supersedes: None
- Superseded by: None

## Context

The architecture proposal identifies product-security responsibilities requiring clarification. CRA applicability depends on supply model and product boundary; Docker alone does not settle scope.

## Decision

Maintain a CRA applicability assessment, manufacturer/maintainer ownership, threat model, SBOM, vulnerability disclosure process, release security evidence and support plan. Treat legal conclusions as pending qualified review. Track GDPR separately. Use the sources in the CRA checklist as starting references, not a declaration of conformity.

## Alternatives considered

Assuming all browser applications are in scope or that all student projects are exempt can produce incorrect obligations. Deferring security until classification is complete creates avoidable risk.

## Consequences

Security engineering proceeds now; legal duties and conformity steps remain conditional on scope. Funding and ownership after the student project must be resolved.

## Validation and revisit triggers

Approve a documented supply-model assessment and name the maintenance owner before production handover. Reassess on redistribution, native client delivery or commercialization.

## Basis and related records

Basis: [Sprint 0 artifact](../../anforderungen/fachlich/Artefakte_UserTeam_Sprint0.md), [quality scenarios](../../anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md) and [architecture overview](../Seezeit-Architektur.md). This record is a proposal, not evidence of implementation or approval.

See the [decision index](README.md) for related decisions.
