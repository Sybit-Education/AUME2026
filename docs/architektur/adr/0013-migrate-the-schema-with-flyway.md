# ADR-0013: Schema mit Flyway migrieren

- Status: Proposed
- Created: 2026-11-18
- Decision owner: Integrationsteam
- Reviewers: Betroffene Lead Developer
- Accepted by / date: Pending
- Supersedes: None
- Superseded by: None

## Context

In der frühen Projektphase ändert sich das Schema mit den Features. Große Migrationsstände würden diese Änderungen bremsen. Eine dauerhafte Datenbank braucht später nachvollziehbare, wiederholbare Schemaänderungen. PostgreSQL ist dafür vorgesehen, aber noch nicht entschieden.

## Decision

Vorschlag: Sobald das Schema die frühe Phase verlässt, übernimmt Flyway die Änderungen und `ddl-auto: create-drop` endet. Bis dahin bleiben die JPA-Entities das Schema. Hibernate baut es beim Start neu auf, und `SampleData` legt die Startdaten an. Feature-Teams schreiben in dieser Phase keine Migrationen. [ADR-0005](0005-use-postgresql-and-private-attachment-storage.md) bleibt ein Vorschlag. Dieses ADR setzt Flyway nicht um.

## Alternatives considered

`ddl-auto: update` verändert eine bestehende Datenbank ohne nachvollziehbare Schritte und reicht für einen späteren Betrieb nicht. Migrationen von Beginn an machen jede frühe Schemaänderung zu einem versionierten Schritt, den mehrere Teams gleichzeitig anfassen.

## Consequences

Bis zur Annahme bleibt der Datenstand an den Neustart im Profil `dev` gebunden. Danach werden Schemaänderungen zu versionierten Skripten, und Startdaten dürfen bestehende Zeilen nicht bei jedem Start überschreiben. Flyway setzt eine dauerhafte Datenbank voraus und folgt der Entscheidung über PostgreSQL.

## Validation and revisit triggers

Flyway wird aktuell, wenn das Schema nicht mehr bei jedem Start neu entstehen soll oder PostgreSQL angenommen wird. Vorher einen leeren und einen bereits gefüllten Stand mit derselben Migrationsfolge hochfahren.

## Basis and related records

[ADR-0005](0005-use-postgresql-and-private-attachment-storage.md), [Datenmodell](../datenmodell.md) und [Entscheidungsindex](README.md). Dieses ADR ist ein Vorschlag und kein Nachweis der Umsetzung.
