# Kontroll-Historie der Objektbetreuung

Die Objektbetreuung sieht die abgeschlossenen Kontrollen ihrer Wohnanlage, kann sie filtern und jede Kontrolle als Protokoll ansehen. In der laufenden Kontrolle sieht sie außerdem die früheren Mängel der WG. Umgesetzt sind die Board-Karten #40 (Historie), #42 (Filter), #43 (Detailansicht), #14 (US-51 Vorgeschichte der WG vor Ort sehen) und #11 (US-40 Protokoll als PDF). Baut auf dem Feature [Kontrolle durch die Objektbetreuung](../checklist/README.md) auf.

## Ablauf

1. Unter **Kontrollen** gibt es die Reiter **Aktuell** (`/inspections`, mit den Tabellen [Anfragen](../kontroll-anfragen/README.md) und Anstehende Kontrollen) und **Historie** (`/inspections/history`).
2. Die Historie listet Kontrollen im Status `FINALIZED` oder `PUBLISHED`, neueste zuerst. Die Spalten folgen demselben Schema wie die Tabellen unter Aktuell: Wohnanlage, WG, Kontrolle (Erst- oder Nachkontrolle), Datum, Ergebnis, Mängel (Anzahl `DEFICIENT`; vor Ort geklärte zählen nicht) und prüfende Person.
3. Sortieren und Filtern direkt in der Kopfzeile der Tabelle:
   - Ein Klick auf eine Überschrift (alle Spalten außer „Ansehen“) sortiert nach dieser Spalte; ein zweiter Klick dreht die Richtung. Datum beginnt mit „neueste zuerst“, alle anderen aufsteigend. „Mängel“ sortiert nach der Anzahl der Mängel; das Backend zählt sie dafür in der Abfrage.
   - Das Trichter-Symbol neben Wohnanlage, WG, Kontrolle, Datum, Ergebnis und Mängel öffnet ein kleines Menü mit dem Filter dieser Spalte: Teil des Namens der Wohnanlage, Teil der WG-Bezeichnung, Erst- oder Nachkontrolle, Zeitraum (von/bis, beide Tage inklusive; Eingabe als Tag/Monat/Jahr, z. B. `28.03.2026`, oder über den Kalender-Knopf), Ergebnis, mit oder ohne Mängel. Bei „Kontrolle“, „Ergebnis“ und „Mängel“ stehen die Optionen als Liste im App-Design (kein `<select>`, dessen Liste der Browser zeichnet); ein Klick filtert sofort. Ein gefüllter Trichter zeigt einen aktiven Filter.
   - Aktive Filter stehen zusätzlich als Chips über der Tabelle; ein Klick entfernt den jeweiligen Filter, „Zurücksetzen“ alle.
   - Filter und Sortierung stehen in der Adresse (`residence`, `flat`, `result`, `followUp`, `defects`, `from`, `to`, `sort`, `dir`) und bleiben beim Neuladen und Teilen des Links erhalten. Ungültige Werte werden ignoriert; liegt „von“ nach „bis“, erscheint ein Hinweis.

   Abweichung vom Wireframe: Dort steht eine Filterleiste über der Tabelle. Auf Wunsch des Teams sitzen Sortierung und Filter in der Kopfzeile. Die Bausteine dafür (`tableControls.tsx`, `tableState.ts`) nutzen auch die Tabellen Anfragen und Anstehend; dort wird im Browser gefiltert, weil die Listen vollständig geladen sind.
4. **Ansehen** öffnet `/inspections/{id}`. Bei abgeschlossenen Kontrollen zeigt die Seite zusätzlich Prüfdatum, prüfende Person und das **Protokoll**: je Bereich die Mängel mit Schwere, Notiz, „vor Ort geklärt“ und Gebäudeursache. Die Seite ist dann nur lesend. Die Bereichsübersicht („Zusammenfassung“) entfällt dort, weil das Protokoll die Bereiche schon zeigt.
5. **Als PDF herunterladen** lädt das Protokoll als Datei `Kontrollprotokoll_<WG>_<Datum>.pdf`: Wohnanlage, WG, Art der Kontrolle, Prüfdatum (ohne Uhrzeit), prüfende Person, Zahl der Mängel, Gesamtergebnis und je Bereich alle Kriterien mit Bewertung, Schwere, Notiz und Gebäudeursache. Sprache wie in der Oberfläche (de/en).

Datumsangaben stehen überall als Tag/Monat/Jahr, auch auf Englisch (`28/03/2026`). Das Datumsfeld im Filter ist deshalb ein Textfeld statt `type="date"`, dessen Format vom Browser abhängt; in der Adresse bleibt das Datum ISO (`from=2026-03-28`).

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

Keine neuen Tabellen. Für das PDF kommt die Bibliothek OpenPDF (`com.github.librepdf:openpdf`, LGPL/MPL) dazu. Gelesen werden `inspections` und `inspection_findings` (Paket `inspection`, `InspectionHistoryService` und `PreviousFindingsService`).

## API

| Methode und Pfad | Zweck |
| --- | --- |
| `GET /api/inspections/history?page=&size=&residence=&flat=&result=&followUp=&defects=&from=&to=&orderBy=&direction=` | abgeschlossene Kontrollen der eigenen Wohnanlage mit `residenceName`, paginiert; `orderBy` = `DATE`, `RESIDENCE`, `FLAT`, `KIND`, `RESULT`, `DEFECTS`, `INSPECTOR` |
| `GET /api/inspections/{id}` | wie bisher; liefert zusätzlich `inspectedAt` und `inspectorName` |
| `GET /api/inspections/{id}/previous-findings` | Vorgeschichte: bis zu drei abgeschlossene Kontrollen derselben WG vor dieser Kontrolle, neueste zuerst |
| `GET /api/inspections/{id}/protocol.pdf` | Protokoll als PDF (`Content-Disposition: attachment`, `Cache-Control: no-store`); Sprache aus `Accept-Language`. Nur abgeschlossene Kontrollen, sonst 409; Zugriff wie bei `GET /api/inspections/{id}` |

Parameter der Historie, alle optional:

| Parameter | Werte |
| --- | --- |
| `flat` | Teil der WG-Bezeichnung, ohne Groß-/Kleinschreibung, höchstens 80 Zeichen. `%` und `_` gelten als normale Zeichen. |
| `result` | `PASSED` oder `FAILED` |
| `followUp` | `true` nur Nachkontrollen (`round` > 0), `false` nur Erstkontrollen |
| `from`, `to` | ISO-Datum (`2026-11-01`), Kalendertag in `Europe/Berlin`, inklusive |
| `orderBy` | `DATE` (Standard), `FLAT`, `KIND` (Erst-/Nachkontrolle), `RESULT`, `INSPECTOR` |
| `direction` | `ASC` oder `DESC`; Standard `DESC` bei `DATE`, sonst `ASC` |

Bei gleichen Werten entscheidet das Prüfdatum (neueste zuerst), dann `id`. Nur diese Spalten sind sortierbar; der allgemeine Spring-Parameter `sort` wird ignoriert. Ungültige Werte und `from` nach `to` ergeben 400.

Antwort je Eintrag: `{id, flatLabel, inspectedAt, round, status, overallResult, deficiencies, inspectorName}`.

Vorgeschichte: Liste von `{id, inspectedAt, round, overallResult, defects}`; `defects` enthält je Befund mit `DEFICIENT` oder `RESOLVED_ON_SITE` die Felder `{area, criterionTitle, rating, severity, note, buildingCauseSuspected}` in Checklisten-Reihenfolge. „Vor dieser Kontrolle“ heißt: vor ihrem `inspectedAt`, bei einer noch nicht gestarteten Kontrolle vor jetzt. Die Kontrolle selbst und andere WGs sind nie enthalten. Zugriff wie bei `GET /api/inspections/{id}`.

## Entwicklungsdaten

`InspectionSampleData` legt im Profil `dev` zusätzlich drei abgeschlossene Kontrollen an: WG S-01 vor 30 Tagen nicht bestanden (zwei Mängel und ein vor Ort geklärter Befund), deren Nachkontrolle vor 23 Tagen bestanden, und WG S-02 vor 28 Tagen bestanden. Anmeldung als `caretaker`. Die geplante Kontrolle von S-01 zeigt damit eine Vorgeschichte mit Hinweisen an „Herd & Backofen“, „Mülltrennung“ und „Dusche & WC“.

## Prüfung

- Backend: `InspectionHistoryTests` (Rechte, Standardsortierung, Sortieren nach jeder Spalte, unbekannte Spalten, alle Filter, Platzhalterzeichen, ungültige Filter, fremde Wohnanlage, Detailansicht, frisch abgeschlossene Kontrolle erscheint in der Historie)
- Backend: `ProtocolPdfTests` (Rechte, Inhalt und Dateiname, Sprache, offene Kontrolle 409, fremde Wohnanlage und unbekannte ID)
- Backend: `PreviousFindingsTests` (Rechte, Reihenfolge und Inhalt, nur frühere Kontrollen derselben WG, höchstens drei, gestartete Kontrolle, fremde Wohnanlage und unbekannte ID)

Historien- und Vorgeschichtstests legen pro Test eigene Wohnanlagen, Kontrollen und Checklisten in ihrer jeweiligen transaktionalen In-Memory-H2-Datenbank an. Sortierung und Auswahl werden anhand der IDs dieser Kontrollen geprüft. Die gemeinsamen Dev-Beispieldaten bleiben für die manuelle Prüfung verfügbar.
- Frontend: `features/inspections/history.test.ts` (Filter und Sortierung aus der Adresse lesen, ungültige Werte verwerfen, Zeitraum prüfen, Klick auf Überschrift, Filter setzen und entfernen, Datum als Tag/Monat/Jahr lesen und anzeigen)
- Frontend: `features/inspections/previous.test.ts` (jüngster früherer Befund je Kriterium, Zuordnung über Bereich und Titel)

## Offen

- Frontend-Komponententests für Historie, Protokoll und Vorgeschichte fehlen.
- **An WG übermitteln** aus dem Wireframe gehört zum Ergebnis-Feature.
- Etage-Filter, sobald `flats` eine Etage hat.
