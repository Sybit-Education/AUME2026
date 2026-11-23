# Kontroll-Historie der Objektbetreuung

Die Objektbetreuung sieht die abgeschlossenen Kontrollen ihrer Wohnanlage, kann sie filtern und jede Kontrolle als Protokoll ansehen. Umgesetzt sind die Board-Karten #40 (Historie), #42 (Filter) und #43 (Detailansicht). Baut auf dem Feature [Kontrolle durch die Objektbetreuung](../checklist/README.md) auf.

## Ablauf

1. Unter **Kontrollen** gibt es die Reiter **Offen** (`/inspections`, wie bisher) und **Historie** (`/inspections/history`).
2. Die Historie listet Kontrollen im Status `FINALIZED` oder `PUBLISHED`, neueste zuerst: Datum, WG, Erst- oder Nachkontrolle, Gesamtergebnis, Anzahl der Mängel (`DEFICIENT`; vor Ort geklärte zählen nicht) und prüfende Person.
3. Filter: WG (Teil der Bezeichnung), Ergebnis, Erst- oder Nachkontrolle und Zeitraum (von/bis, beide Tage inklusive). Die Filter stehen in der Adresse und bleiben beim Neuladen und Teilen des Links erhalten. Ungültige Werte in der Adresse werden ignoriert; liegt „von“ nach „bis“, erscheint ein Hinweis statt einer leeren Liste.
4. **Ansehen** öffnet `/inspections/{id}`. Bei abgeschlossenen Kontrollen zeigt die Seite zusätzlich Prüfdatum, prüfende Person und das **Protokoll**: je Bereich die Mängel mit Schwere, Notiz, „vor Ort geklärt“ und Gebäudeursache. Die Seite ist dann nur lesend.

Ein Etage-Filter wie im Wireframe fehlt, weil `flats` keine Etage speichert.

## Rollen und Daten

| Rolle | Berechtigung | Zugriff |
| --- | --- | --- |
| Objektbetreuung (`CARETAKER`) | `INSPECTION_PERFORM` | nur Kontrollen von WGs der eigenen Wohnanlage; fremde Kontrollen antworten mit 404 |
| Studierende, Sachbearbeitung | – | 403 |

Keine neuen Tabellen. Gelesen werden `inspections` und `inspection_findings` (Paket `inspection`, `InspectionHistoryService`).

## API

| Methode und Pfad | Zweck |
| --- | --- |
| `GET /api/inspections/history?page=&size=&flat=&result=&followUp=&from=&to=` | abgeschlossene Kontrollen der eigenen Wohnanlage, paginiert |
| `GET /api/inspections/{id}` | wie bisher; liefert zusätzlich `inspectedAt` und `inspectorName` |

Parameter der Historie, alle optional:

| Parameter | Werte |
| --- | --- |
| `flat` | Teil der WG-Bezeichnung, ohne Groß-/Kleinschreibung, höchstens 80 Zeichen. `%` und `_` gelten als normale Zeichen. |
| `result` | `PASSED` oder `FAILED` |
| `followUp` | `true` nur Nachkontrollen (`round` > 0), `false` nur Erstkontrollen |
| `from`, `to` | ISO-Datum (`2026-11-01`), Kalendertag in `Europe/Berlin`, inklusive |

Sortiert wird immer nach `inspectedAt` absteigend, dann `id`; ein `sort`-Parameter wird ignoriert. Ungültige Werte und `from` nach `to` ergeben 400.

Antwort je Eintrag: `{id, flatLabel, inspectedAt, round, status, overallResult, deficiencies, inspectorName}`.

## Entwicklungsdaten

`InspectionSampleData` legt im Profil `dev` zusätzlich drei abgeschlossene Kontrollen an: WG S-01 vor 30 Tagen nicht bestanden (zwei Mängel, einer vor Ort geklärt), deren Nachkontrolle vor 23 Tagen bestanden, und WG S-02 vor 28 Tagen bestanden. Anmeldung als `caretaker`.

## Prüfung

- Backend: `InspectionHistoryTests` (Rechte, Sortierung, alle Filter, Platzhalterzeichen, ungültige Filter, fremde Wohnanlage, Detailansicht, frisch abgeschlossene Kontrolle erscheint in der Historie)
- Frontend: `features/inspections/history.test.ts` (Filter aus der Adresse lesen, ungültige Werte verwerfen, Zeitraum prüfen)

## Offen

- Frontend-Komponententests für Historie und Protokoll fehlen.
- **Protokoll herunterladen** und **An WG übermitteln** aus dem Wireframe gehören zu US-40 bzw. zum Ergebnis-Feature.
- Etage-Filter, sobald `flats` eine Etage hat.
