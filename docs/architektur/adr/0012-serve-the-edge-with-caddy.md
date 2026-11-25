# ADR-0012: Caddy als Reverse Proxy und Dateiserver

- Status: Proposed
- Created: 2026-11-18
- Decision owner: Integrationsteam
- Reviewers: Betroffene Lead Developer
- Accepted by / date: Pending
- Supersedes: None
- Superseded by: None

## Context

Die gebaute React-Anwendung und die API sollen unter demselben HTTPS-Origin erreichbar sein ([ADR-0004](0004-provide-a-responsive-browser-frontend.md)). Der Betrieb läuft über Docker Compose ([ADR-0002](0002-deploy-the-application-using-docker-compose.md)). Heute liefert das Frontend-Image die gebauten Dateien mit Nginx aus und leitet `/api` an das Backend weiter. Der Architekturentwurf für das Frontend steht noch aus.

## Decision

Vorschlag: Caddy ist der einzige Reverse Proxy und zugleich der Dateiserver für die gebauten Frontend-Assets. Browser erreichen Oberfläche und API über denselben Origin. Dieses ADR beschreibt die Richtung und setzt sie nicht um. Das bestehende Nginx-Image bleibt, bis der Frontend-Entwurf vorliegt und dieses ADR akzeptiert ist.

## Alternatives considered

Nginx bleibt der heutige Weg und kann Proxy und Dateiauslieferung weiter übernehmen. Ein separater Dateiserver neben dem Proxy verdoppelt die Komponente, die Zertifikat und Origin verantwortet.

## Consequences

Ein Prozess übernimmt TLS, Weiterleitung und statische Dateien. Die Frontend-Auslieferung hängt am Proxy statt am eigenen Nginx-Image. Solange dieses ADR nur vorgeschlagen ist, ändert sich weder Image noch Compose.

## Validation and revisit triggers

Nach dem Frontend-Entwurf prüfen, ob Caddy die gebauten Assets und `/api` unter einem Origin ausliefert und nur die vorgesehenen Ports offen sind. Wird der Entwurf einen anderen Webserver festlegen, dieses ADR vor einer Annahme neu bewerten.

## Basis and related records

[ADR-0002](0002-deploy-the-application-using-docker-compose.md), [ADR-0004](0004-provide-a-responsive-browser-frontend.md) und [Entscheidungsindex](README.md). Dieses ADR ist ein Vorschlag und kein Nachweis der Umsetzung.
