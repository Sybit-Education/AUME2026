# Kontrolle durch die Objektbetreuung

Die Objektbetreuung arbeitet bei einer Kontrolle die veröffentlichte Checkliste einer WG ab, erfasst Mängel und legt das Gesamtergebnis fest. Aufbau und Texte folgen dem klickbaren Wireframe (Ansicht Objektbetreuung → Kontrolle).

## Ablauf

1. **Kontrollen** (`/inspections`) listet die geplanten und laufenden Kontrollen der eigenen Wohnanlage.
2. **Kontrolle starten** bindet die aktuell veröffentlichte Checkliste unveränderlich an die Kontrolle (ADR-0007) und setzt den Status von `PLANNED` auf `DRAFT`.
3. Je Bereich (Küche, Boden, Treppe, Bad) zeigt die Seite die Kriterien mit Beschreibung. Bewertet wird über:
   - **Bereich in Ordnung**: alle noch unbewerteten Kriterien des Bereichs auf `OK`
   - **Alles in Ordnung**: alle noch unbewerteten Kriterien der Kontrolle auf `OK`
   - **+ Mangel erfassen**: Kriterium, Schwere (Normal = `LIGHT`, Schwer = `SEVERE`) und Notiz (Pflicht) → `DEFICIENT`
   - je Mangel **Vor Ort geklärt** (`RESOLVED_ON_SITE`), **Gebäudeursache** (`buildingCauseSuspected`) und **Löschen**
4. **Zum Abschluss** zeigt den Stand je Bereich. Das Gesamtergebnis (Bestanden = `PASSED`, Nachkontrolle erforderlich = `FAILED`) wählt die prüfende Person; vorgeschlagen wird `FAILED`, sobald ein offener Mangel existiert (US-34). Abschließen geht erst, wenn alle Kriterien bewertet sind, und setzt den Status auf `FINALIZED`.

## Rollen und Daten

| Rolle | Berechtigung | Zugriff |
| --- | --- | --- |
| Objektbetreuung (`CARETAKER`) | `INSPECTION_PERFORM` | nur Kontrollen von WGs der eigenen Wohnanlage; fremde Kontrollen antworten mit 404 |
| Studierende, Sachbearbeitung | – | 403 |

Daten: `inspections` und `inspection_findings` (Paket `inspection`), Kriterien aus `checklist_versions`/`checklist_criteria` über `ChecklistService` (Paket `standard`). Je Kontrolle und Kriterium gibt es höchstens einen Befund.

## API

Alle Endpunkte verlangen `INSPECTION_PERFORM`. Änderungen sind nur im Status `DRAFT` erlaubt (sonst 409) und liefern die aktualisierte Kontrolle zurück.

| Methode und Pfad | Zweck |
| --- | --- |
| `GET /api/inspections?page=&size=` | offene Kontrollen der eigenen Wohnanlage (paginiert) |
| `GET /api/inspections/{id}` | Kontrolle mit Kriterien und Befunden |
| `POST /api/inspections/{id}/start` | `PLANNED` → `DRAFT`, Checkliste binden |
| `PUT /api/inspections/{id}/findings/{criterionId}` | Befund setzen: `{rating, severity, note, buildingCauseSuspected}` |
| `DELETE /api/inspections/{id}/findings/{criterionId}` | Befund entfernen |
| `POST /api/inspections/{id}/mark-ok` | `{area}` oder ohne Body: unbewertete Kriterien auf `OK` |
| `POST /api/inspections/{id}/finalize` | `{overallResult}`, `DRAFT` → `FINALIZED` |

## Entwicklungsdaten

Im Profil `dev` legt `InspectionSampleData` eine veröffentlichte Checkliste (Fassung 1, zehn Kriterien aus dem Wireframe), die WGs 4.12 und 4.13 in der Wohnanlage Sonne und je eine geplante Kontrolle an. Anmeldung als `caretaker`.

## Prüfung

- Backend: `InspectionTests` (Rechte, Ablauf bis zum Abschluss, fremde Wohnanlage, idempotente Startdaten)
- Frontend: `features/inspections/summary.test.ts` (Gruppierung nach Bereichen, Ergebnisvorschlag)

## Offen

- **Anwesende Bewohnende** (Feld im Wireframe-Abschluss) hat kein Feld im Datenmodell; mit dem Integrationsteam klären.
- **Fotos** zu Mängeln (`inspection_finding_photos`) und **Vergleichsbilder** je Kriterium (`criterion_images`) sind noch nicht angebunden.
- **Offline-Entwurf** bei Netzverlust (ADR-0011) fehlt.
- **Seitenleiste** aus dem Wireframe: die Navigation nutzt bis dahin die gemeinsame Kopfzeile.
- Veröffentlichen an die Studierenden (`FINALIZED` → `PUBLISHED`) und Nachkontrollen gehören zum Ergebnis-Feature.
