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

Gleichzeitige Änderungen: Jede ändernde Anfrage lädt die Kontrolle mit `InspectionRepository.findLockedById` (`SELECT … FOR UPDATE`). Anfragen an dieselbe Kontrolle laufen dadurch nacheinander; so entstehen keine doppelten Befunde, und eine Kontrolle kann nicht abgeschlossen werden, während gleichzeitig ein Befund gelöscht wird. Das `@Version`-Feld allein reicht dafür nicht, weil sich bei Befundänderungen die Kontrolle selbst nicht ändert.

## API

Alle Endpunkte verlangen `INSPECTION_PERFORM`. Änderungen sind nur im Status `DRAFT` erlaubt (sonst 409) und liefern die aktualisierte Kontrolle zurück.

| Methode und Pfad | Zweck |
| --- | --- |
| `GET /api/inspections?page=&size=` | offene Kontrollen der eigenen Wohnanlage (paginiert, standardmäßig nach `id` sortiert) |
| `GET /api/inspections/{id}` | Kontrolle mit Kriterien und Befunden |
| `POST /api/inspections/{id}/start` | `PLANNED` → `DRAFT`, Checkliste binden; 409 ohne veröffentlichte Checkliste oder wenn sie keine Kriterien hat |
| `PUT /api/inspections/{id}/findings/{criterionId}` | Befund setzen: `{rating, severity, note, buildingCauseSuspected}`. Die Notiz wird getrimmt und darf danach höchstens 2000 Zeichen haben; `severity` und `buildingCauseSuspected` werden bei `OK` nicht gespeichert |
| `DELETE /api/inspections/{id}/findings/{criterionId}` | Befund entfernen |
| `POST /api/inspections/{id}/mark-ok` | `{area}` oder ohne Body: unbewertete Kriterien auf `OK` |
| `POST /api/inspections/{id}/finalize` | `{overallResult}`, `DRAFT` → `FINALIZED`; 409, solange ein Kriterium unbewertet ist oder die Checkliste keine Kriterien hat |

Das Frontend zeigt bei 409 eine eigene Meldung und lädt die Kontrolle neu. Bei 401 leitet es zur Anmeldung weiter (Rückkehr zur Kontrolle), bei 403 auf `/forbidden`. „Kontrolle starten“ erscheint nur, wenn die Checkliste Kriterien hat. Der Mangel-Dialog wählt ein Kriterium ohne bestehenden Mangel vor und weist darauf hin, wenn Speichern einen bestehenden Mangel ersetzt.

## Entwicklungsdaten

Im Profil `dev` legt `InspectionSampleData` eine veröffentlichte Checkliste (Fassung 1, zehn Kriterien aus dem Wireframe), für die WGs S-01 und S-02 der Wohnanlage Sonne (aus `SampleData`) je eine geplante Kontrolle an. Anmeldung als `caretaker`.

## Prüfung

- Backend: `InspectionTests` (Rechte, Ablauf bis zum Abschluss, fremde Wohnanlage, idempotente Startdaten, Notizlänge, Gebäudeursache nur bei Mängeln, Checkliste ohne Kriterien)
- Backend: `InspectionConcurrencyTests` (parallele Bewertungen, „Alles in Ordnung“ parallel zu Bewertungen, Abschließen parallel zum Löschen; ohne `@Transactional`, damit die Sperre wirkt)
- Frontend: `features/inspections/summary.test.ts` (Gruppierung nach Bereichen, Ergebnisvorschlag)

## Offen

- **Anwesende Bewohnende** (Feld im Wireframe-Abschluss) hat kein Feld im Datenmodell; mit dem Integrationsteam klären.
- **Fotos** zu Mängeln (`inspection_finding_photos`) und **Vergleichsbilder** je Kriterium (`criterion_images`) sind noch nicht angebunden.
- **Offline-Entwurf** bei Netzverlust (ADR-0011) fehlt.
- **Seitenleiste** aus dem Wireframe: die Navigation nutzt bis dahin die gemeinsame Kopfzeile.
- **Frontend-Tests** für Weiterleitung bei 401/403, Neuladen bei 409 und den Hinweis im Mangel-Dialog fehlen.
- Veröffentlichen an die Studierenden (`FINALIZED` → `PUBLISHED`) und Nachkontrollen gehören zum Ergebnis-Feature.
