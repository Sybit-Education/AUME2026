# Kontroll-Historie der Objektbetreuung

Die Objektbetreuung sieht die abgeschlossenen Kontrollen ihrer Wohnanlage, kann sie filtern und jede Kontrolle als Protokoll ansehen. In der laufenden Kontrolle sieht sie außerdem die früheren Mängel der WG. Umgesetzt sind die Board-Karten #40 (Historie), #42 (Filter), #43 (Detailansicht) und #14 (US-51 Vorgeschichte der WG vor Ort sehen). Baut auf dem Feature [Kontrolle durch die Objektbetreuung](../checklist/README.md) auf.

## Ablauf

1. Unter **Kontrollen** gibt es die Reiter **Offen** (`/inspections`, wie bisher) und **Historie** (`/inspections/history`).
2. Die Historie listet Kontrollen im Status `FINALIZED` oder `PUBLISHED`, neueste zuerst: Datum, WG, Erst- oder Nachkontrolle, Gesamtergebnis, Anzahl der Mängel (`DEFICIENT`; vor Ort geklärte zählen nicht) und prüfende Person.
3. Filter: WG (Teil der Bezeichnung), Ergebnis, Erst- oder Nachkontrolle und Zeitraum (von/bis, beide Tage inklusive). Die Filter stehen in der Adresse und bleiben beim Neuladen und Teilen des Links erhalten. Ungültige Werte in der Adresse werden ignoriert; liegt „von“ nach „bis“, erscheint ein Hinweis statt einer leeren Liste.
4. **Ansehen** öffnet `/inspections/{id}`. Bei abgeschlossenen Kontrollen zeigt die Seite zusätzlich Prüfdatum, prüfende Person und das **Protokoll**: je Bereich die Mängel mit Schwere, Notiz, „vor Ort geklärt“ und Gebäudeursache. Die Seite ist dann nur lesend.

Ein Etage-Filter wie im Wireframe fehlt, weil `flats` keine Etage speichert.

### Vorgeschichte der WG in der Kontrolle (US-51)

Beim Abarbeiten der Checkliste (Status `PLANNED` oder `DRAFT`) zeigt die Kontrollseite oben die Karte **Vorgeschichte der WG**: die letzten drei abgeschlossenen Kontrollen derselben WG mit Datum, Erst- oder Nachkontrolle, Ergebnis und ihren Mängeln (Bereich, Kriterium, Schwere, Notiz, Gebäudeursache, „vor Ort geklärt“). Gibt es frühere Mängel, ist die Karte aufgeklappt, sonst zugeklappt. Der Aufbau folgt „Frühere Kontrollen“ aus dem Wireframe; eine eigene Ansicht für die Objektbetreuung zeigt das Wireframe nicht.

Zusätzlich trägt jedes Kriterium, das in einer dieser Kontrollen auffällig war, den Hinweis **„früher auffällig · Datum“**; aufgeklappt steht die zuletzt notierte Bemerkung darunter. „Auffällig“ statt „Mangel“, weil auch vor Ort geklärte Befunde zählen, die fachlich kein Mangel sind. Zugeordnet wird über Bereich und Kriteriumstitel, nicht über die ID, weil jede Checklisten-Fassung eigene Kriterien-IDs hat.

Ältere Kontrollen stehen in der Historie. Das gezielte Nachprüfen nur der offenen Mängel einer Nachkontrolle ist US-44 und nicht Teil davon.

## Rollen und Daten

| Rolle | Berechtigung | Zugriff |
| --- | --- | --- |
| Objektbetreuung (`CARETAKER`) | `INSPECTION_PERFORM` | nur Kontrollen von WGs der eigenen Wohnanlage; fremde Kontrollen antworten mit 404 |
| Studierende, Sachbearbeitung | – | 403 |

Keine neuen Tabellen. Gelesen werden `inspections` und `inspection_findings` (Paket `inspection`, `InspectionHistoryService` und `PreviousFindingsService`).

## API

| Methode und Pfad | Zweck |
| --- | --- |
| `GET /api/inspections/history?page=&size=&flat=&result=&followUp=&from=&to=` | abgeschlossene Kontrollen der eigenen Wohnanlage, paginiert |
| `GET /api/inspections/{id}` | wie bisher; liefert zusätzlich `inspectedAt` und `inspectorName` |
| `GET /api/inspections/{id}/previous-findings` | Vorgeschichte: bis zu drei abgeschlossene Kontrollen derselben WG vor dieser Kontrolle, neueste zuerst |

Parameter der Historie, alle optional:

| Parameter | Werte |
| --- | --- |
| `flat` | Teil der WG-Bezeichnung, ohne Groß-/Kleinschreibung, höchstens 80 Zeichen. `%` und `_` gelten als normale Zeichen. |
| `result` | `PASSED` oder `FAILED` |
| `followUp` | `true` nur Nachkontrollen (`round` > 0), `false` nur Erstkontrollen |
| `from`, `to` | ISO-Datum (`2026-11-01`), Kalendertag in `Europe/Berlin`, inklusive |

Sortiert wird immer nach `inspectedAt` absteigend, dann `id`; ein `sort`-Parameter wird ignoriert. Ungültige Werte und `from` nach `to` ergeben 400.

Antwort je Eintrag: `{id, flatLabel, inspectedAt, round, status, overallResult, deficiencies, inspectorName}`.

Vorgeschichte: Liste von `{id, inspectedAt, round, overallResult, defects}`; `defects` enthält je Befund mit `DEFICIENT` oder `RESOLVED_ON_SITE` die Felder `{area, criterionTitle, rating, severity, note, buildingCauseSuspected}` in Checklisten-Reihenfolge. „Vor dieser Kontrolle“ heißt: vor ihrem `inspectedAt`, bei einer noch nicht gestarteten Kontrolle vor jetzt. Die Kontrolle selbst und andere WGs sind nie enthalten. Zugriff wie bei `GET /api/inspections/{id}`.

## Entwicklungsdaten

`InspectionSampleData` legt im Profil `dev` zusätzlich drei abgeschlossene Kontrollen an: WG S-01 vor 30 Tagen nicht bestanden (zwei Mängel und ein vor Ort geklärter Befund), deren Nachkontrolle vor 23 Tagen bestanden, und WG S-02 vor 28 Tagen bestanden. Anmeldung als `caretaker`. Die geplante Kontrolle von S-01 zeigt damit eine Vorgeschichte mit Hinweisen an „Herd & Backofen“, „Mülltrennung“ und „Dusche & WC“.

## Prüfung

- Backend: `InspectionHistoryTests` (Rechte, Sortierung, alle Filter, Platzhalterzeichen, ungültige Filter, fremde Wohnanlage, Detailansicht, frisch abgeschlossene Kontrolle erscheint in der Historie)
- Backend: `PreviousFindingsTests` (Rechte, Reihenfolge und Inhalt, nur frühere Kontrollen derselben WG, höchstens drei, gestartete Kontrolle, fremde Wohnanlage und unbekannte ID)
- Frontend: `features/inspections/history.test.ts` (Filter aus der Adresse lesen, ungültige Werte verwerfen, Zeitraum prüfen)
- Frontend: `features/inspections/previous.test.ts` (jüngster früherer Befund je Kriterium, Zuordnung über Bereich und Titel)

## Offen

- Frontend-Komponententests für Historie, Protokoll und Vorgeschichte fehlen.
- **Protokoll herunterladen** und **An WG übermitteln** aus dem Wireframe gehören zu US-40 bzw. zum Ergebnis-Feature.
- Etage-Filter, sobald `flats` eine Etage hat.
