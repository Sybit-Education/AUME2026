# ADR-0006: Centralize authentication and enforce scoped authorization

- Status: Proposed
- Created: 2026-10-29
- Decision owner: Integrationsteam
- Reviewers: Affected Lead Developers; responsible PO and Seezeit-IT as applicable
- Accepted by / date: Pending
- Supersedes: None
- Superseded by: None

## Context

Residents, administrative staff and inspectors have distinct rights. US-03 restricts inspectors to assigned WGs; ST-S5 restricts private tasks.

## Decision

Use Keycloak with optional Seezeit federation. Require staff MFA. Backend authorization combines role, residence assignment, WG membership and membership dates. Use short-lived single-use invitation tokens bound to intended memberships. Keep WG task APIs, exports and dashboards inaccessible to staff roles.

## Alternatives considered

Frontend-only checks are bypassable. Reusable shared WG links cannot provide attributable, revocable membership access.

## Consequences

Identity operations add a service and administrative responsibility. Existing Seezeit protocol support and account lifecycle integration remain unconfirmed. Infrastructure administrators can still have privileged access; role separation alone is not protection from host operators.

## Validation and revisit triggers

Test cross-WG access, expired memberships, revoked invitations and staff access to task data. Define former-resident access and emergency administrative access before release.

## Basis and related records

Basis: [Sprint 0 artifact](../../anforderungen/fachlich/Artefakte_UserTeam_Sprint0.md), [quality scenarios](../../anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md) and [architecture overview](../Seezeit-Architektur.md). This record is a proposal, not evidence of implementation or approval.

See the [decision index](README.md) for related decisions.
