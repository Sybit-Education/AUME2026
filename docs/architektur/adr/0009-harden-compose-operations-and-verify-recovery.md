# ADR-0009: Harden Compose operations and verify recovery

- Status: Proposed
- Created: 2026-10-29
- Decision owner: Integrationsteam
- Reviewers: Affected Lead Developers; responsible PO and Seezeit-IT as applicable
- Accepted by / date: Pending
- Supersedes: None
- Superseded by: None

## Context

The deployment stores personal records and evidence and needs a maintainable update/recovery path.

## Decision

Expose only intended proxy ports; restrict identity administration. Use non-root processes where supported, minimal capabilities, no privileged containers or Docker socket mounts, digest-pinned images, restricted secret files, health checks, limits and log rotation. Back up database, attachments and identity state encrypted off-host.

## Alternatives considered

Default container privileges and host-local backups reduce setup work but increase compromise and recovery risk.

## Consequences

Host patching, certificate renewal, monitoring and restore drills require an assigned operator. Compose secrets do not encrypt their source files. Proposed RPO 24 hours and RTO eight hours require approval and validation.

## Validation and revisit triggers

Review effective Compose configuration, secret permissions, network exposure and image provenance. Restore a complete deployment on a clean host and measure recovery.

## Basis and related records

Basis: [Sprint 0 artifact](../../anforderungen/fachlich/Artefakte_UserTeam_Sprint0.md), [quality scenarios](../../anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md) and [architecture overview](../Seezeit-Architektur.md). This record is a proposal, not evidence of implementation or approval.

See the [decision index](README.md) for related decisions.
