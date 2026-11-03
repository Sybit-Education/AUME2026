# ADR-0004: Provide a responsive browser frontend

- Status: Accepted
- Created: 2026-10-29
- Decision owner: Project architecture team
- Reviewers: Project architecture team
- Accepted by / date: 2026-10-29
- Supersedes: None
- Superseded by: None

## Context

ST-M6 requires low-effort smartphone use; US-31 requires tablet inspections. Native installation is not required.

## Decision

Use React and TypeScript for a responsive web frontend served through the same HTTPS origin as the API. Provide autosaved drafts and visible save/connection status. Defer full offline synchronization pending pilot connectivity evidence.

## Alternatives considered

Native applications duplicate delivery effort. Full offline-first operation requires local data protection, synchronization and conflict resolution.

## Consequences

One interface supports residents and staff. Network failure can interrupt inspections; drafts must clearly distinguish saved from unsaved changes.

## Validation and revisit triggers

Pilot on actual staff tablets and resident smartphones. Measure connectivity at inspection locations before accepting online-only operation.

## Basis and related records

Basis: [Sprint 0 artifact](../../anforderungen/fachlich/Artefakte_UserTeam_Sprint0.md) and the architecture proposal recorded in this ADR. Status reflects the existing repository record; acceptance does not demonstrate implementation.

See the [decision index](README.md) for related decisions.
