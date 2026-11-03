# ADR-0011: Kontrollerfassung bei Netzabbruch absichern

- Status: Proposed
- Created: 2026-11-03
- Decision owner: Integrationsteam
- Reviewers: Betroffene Lead Developer und zuständiger PO
- Accepted by / date: Pending
- Supersedes: None (Änderung des Offlineumfangs aus ADR-0004 vorgeschlagen)
- Superseded by: None

## Context

Das wichtigste [Qualitätsszenario](../../anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md) verlangt, Ergebnisse und Fotos bei Netzabbruch lokal zu sichern und danach automatisch zu synchronisieren. [ADR-0004](0004-provide-a-responsive-browser-frontend.md) verschiebt vollständige Offline-Synchronisierung. Im [Sprint-0-Artefakt](../../anforderungen/fachlich/Artefakte_UserTeam_Sprint0.md), Abschnitt 5.7, steht US-74 unter „Später“. Priorität, Releaseumfang und Architektur müssen gemeinsam abgestimmt werden.

## Decision

Vorschlag: Die responsive Browseranwendung speichert begonnene Kontrollentwürfe und zugehörige Fotos lokal und führt ausstehende Änderungen nach Netzrückkehr automatisch mit dem Backend zusammen. Lokaler Speicherstand, Serverbestätigung und Konflikte sind sichtbar. Veröffentlichung und Rechteprüfung bleiben serverseitig; veraltete Änderungen dürfen bestätigte Daten nicht still überschreiben.

Dieses ADR ändert ADR-0004 erst nach Annahme. Ein vollständiger Kaltstart ohne Netz und die dafür vorab bereitzustellenden Daten werden separat mit dem PO festgelegt.

## Alternatives considered

Reiner Onlinebetrieb mit Warnhinweis ist einfacher, erfüllt aber das priorisierte Qualitätsziel nicht. Eine native Anwendung schafft einen weiteren Auslieferungsweg und löst Synchronisierungskonflikte ebenfalls nicht von selbst.

## Consequences

Lokale personenbezogene Daten brauchen begrenzten Umfang und eine abgestimmte Löschung bei Synchronisierung, Abmeldung und Benutzerwechsel. Browserspeicher, Gerätekapazität, wiederholte Uploads, konkurrierende Änderungen und abgelaufene Berechtigungen erfordern ein konkretisiertes Verfahren. Das betroffene Feature-Team entwirft es mit dem Integrationsteam; der PO stimmt den Releaseumfang ab.

## Validation and revisit triggers

Netz während Texteingabe und Fotoerfassung trennen, Seite neu laden und Verbindung wiederherstellen. Prüfen: Entwurf bleibt erhalten, Uploads sind wiederholbar, es entstehen keine doppelten Ergebnisse, Konflikte werden angezeigt und abgelaufene Berechtigungen werden beim Serverabgleich abgewiesen. Prüfung auf den vereinbarten Geräten und Browsern; Speichergrenzen und Löschverhalten müssen vor Freigabe geklärt sein.

## Basis and related records

[Architekturübersicht](../Seezeit-Architektur.md), [ADR-0004](0004-provide-a-responsive-browser-frontend.md) und [Entscheidungsindex](README.md).
