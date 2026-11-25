# Kontrolltermin meiner WG sehen (US-15)

Studierende sehen den angekündigten Kontrollzeitraum ihrer WG auf der Startseite. Bei einem vorhandenen Termin führt „Details“ zur direkt aufrufbaren Detailseite `/student/inspection/appointment`.

**Stand:** Der US-15-Lesepfad einschließlich des Änderungshinweises ist implementiert. Die produktive Anbindung der Terminänderung über US-18 und die Integrationsprüfung mit US-01 stehen aus.

## Regeln

- Das Backend ermittelt die WG aus der aktiven Mitgliedschaft der angemeldeten Person. Sie gilt ab `validFrom` bis vor `validTo`. Ein leeres `validTo` bedeutet, dass kein Ende festgelegt ist. `validTo` ist der erste Tag ohne Zugriff. Eine mitgesendete WG-ID beeinflusst die Auswahl nicht.
- Die Abfrage berücksichtigt geplante Kontrollen der eigenen WG mit angekündigtem Zeitraum in der zugehörigen Wohnanlage. Der letzte Tag zählt noch zum Zeitraum. Von den passenden Terminen wird der mit dem frühesten Beginn angezeigt.
- Ohne genau eine aktive WG oder bei mehreren passenden Terminen mit demselben frühesten Beginn liefert die API keinen Termin.
- Start- und Detailseite zeigen den Kontrollzeitraum im selben lokalisierten Datumsformat. Einzeltage erscheinen einmal, bei einem Jahreswechsel tragen beide Daten das Jahr. Die Anzeige enthält ausschließlich den Zeitraum. Das Backend verwendet für Terminauswahl, Mitgliedschaft und Änderungshinweis das aktuelle Kalenderdatum in `Europe/Berlin`.
- Bei einem nachträglich verschobenen, angekündigten Zeitraum sehen Studierende „Termin geändert“ bis zum Tag vor dem neuen Beginn. Ab dem ersten Tag blendet die Oberfläche den Hinweis beim nächsten API-Abruf aus.

Die Kontrollkarte steht unter der Begrüßung. Die Willkommenszeile verwendet den Namen aus `GET /api/me`. Der zusätzliche Gruß aus `GET /api/greeting` enthält den gespeicherten Namen und die Wohnanlage. Sobald das Dashboard angezeigt wird, lädt die Karte den Termin unabhängig nach und zeigt währenddessen einen Ladezustand. Ein Sprachwechsel übersetzt die Karte und formatiert den vorhandenen Zeitraum neu.

## Rechte

Die Startseite setzt eine Anmeldung voraus. Die Kontrollkarte und der Terminabruf auf der Startseite sind auf Personen mit der Rolle `STUDENT` beschränkt. Für `/student/inspection/appointment` prüft der Routen-Loader dieselbe Rolle. Der Backend-Endpunkt prüft `STUDENT` und beschränkt die Daten unabhängig vom Frontend auf die eigene WG.

## API

`GET /api/student/inspection-appointment` liefert bei einem Treffer `id` (Kontroll-ID), `startsOn` und `endsOn` (Kalenderdaten im Format `YYYY-MM-DD`) sowie den verpflichtenden Boolean `appointmentChanged`. Der Wert ist `true`, wenn der Zeitraum angekündigt wurde, `rescheduledAt` gesetzt ist und das Berliner Kalenderdatum vor `startsOn` liegt. Der interne Änderungszeitpunkt bleibt im Backend.

| Ergebnis | Verhalten |
| --- | --- |
| `200 OK` mit `id`, `startsOn`, `endsOn` und `appointmentChanged` | Die Karte zeigt den Zeitraum und bei `appointmentChanged: true` den Änderungshinweis. |
| `204 No Content` | Die Karte zeigt „Keine Kontrolle angekündigt“. |
| `401 Unauthorized` | Die bestehende Sitzungsbehandlung führt zum Login und erhält das Rückkehrziel. |
| `403 Forbidden` | Die Rolle `STUDENT` fehlt. Das Frontend führt zu `/forbidden`. |
| HTTP `500` oder Netzwerkfehler | Auf der Startseite zeigt nur die Kontrollkarte eine übersetzte Fehlermeldung. Begrüßung und Wohnanlage bleiben sichtbar. Auf `/student/inspection/appointment` greift das gemeinsame Routenfehlerlayout. |

## Anbindung an die Sachbearbeitung (US-18)

Bei der erneuten Ankündigung eines bereits angekündigten Kontrollzeitraums mit tatsächlich geändertem Start- oder Enddatum setzt die Sachbearbeitung `InspectionPeriodEntity.rescheduledAt` auf den aktuellen Zeitpunkt. Das geschieht in derselben Transaktion wie die Speicherung des aktualisierten Zeitraums. Vor und bei der Erstankündigung bleibt der Wert leer. Unveränderte Kalendergrenzen lösen keine neue Markierung aus. Jede wirksame weitere Datumsänderung aktualisiert den Zeitpunkt.

Die vorhandene Spalte gehört zu `inspection_periods`. Hibernate erzeugt sie im aktuellen H2-Setup. Persistente Umgebungen benötigen eine abgestimmte Migration mit einer nullable Spalte `rescheduled_at`. [Datenmodell](../../architektur/datenmodell.md#kontrollplanung)

## Entwicklungsdaten

`StudentSampleData.java` ergänzt im Profil `dev` für das Konto `student` die Mitgliedschaft in `S-01` und den angekündigten Kontrollzeitraum 07.12.–11.12.2026 in „Wohnanlage Sonne“. Nach dem 11.12.2026 liefert dieser feste Beispieltermin den Leerzustand. Für den Termin- und Exportfall stehen automatisierte Tests mit eigenen Testdaten zur Verfügung.

1. Das Backend mit dem Spring-Profil `dev` und das Frontend mit Vite starten. Die Befehle für beide Terminals stehen im [Projekt-README](../../../README.md#lokaler-start).
2. Unter `http://localhost:5173/` über „Studierende“ als Dev-Konto `student` anmelden.
3. Den Termin auf der Startseite prüfen, „Details“ öffnen und auf der Detailseite „In Kalender übernehmen“ wählen. `http://localhost:5173/student/inspection/appointment` lässt sich auch direkt öffnen.

## Kalenderexport

Auf `/student/inspection/appointment` exportiert „In Kalender übernehmen“ den gesamten Kontrollzeitraum als ganztägiges `.ics`-Ereignis einschließlich des letzten Tages.

## Tests

- Im Verzeichnis `frontend` führt `npm test` die Format- und Kalender-Unit-Tests sowie die React- und Routingtests aus. `InspectionAppointment.test.tsx` prüft den Hinweis in DE/EN und den Download mit aktualisierten Kalendergrenzen. `StudentInspectionRoutes.test.tsx` prüft Start- und Detailseite, Details-Link, Änderungshinweis, Leerzustand, Rollen und Sitzungsablauf sowie die sichtbare personalisierte Begrüßung bei Terminfehlern.
- Im Verzeichnis `backend` führt `./mvnw verify` beziehungsweise `.\mvnw.cmd verify` unter Windows die Backend-Suite aus. `ApplicationTests` prüft mit MockMvc und In-Memory-H2 unter anderem Zugriffsschutz, Terminauswahl und wiederholtes Seeding.
- `StudentInspectionAppointmentIsolationTests` verwendet eine separate In-Memory-H2-Datenbank (`aume-us15-isolation`). Zwei Studierende in getrennten Sitzungen sehen jeweils nur den Termin ihrer eigenen WG, auch mit fremder WG-ID im Request. Gezielter Aufruf unter Windows: `.\mvnw.cmd "-Dtest=StudentInspectionAppointmentIsolationTests" test`. Unter macOS und Linux lautet der Aufruf `./mvnw -Dtest=StudentInspectionAppointmentIsolationTests test`.

## Ergebnis und Mängel (US-38)

Der Reiter **Ergebnis** (`/student/inspection/result`) zeigt das neueste veröffentlichte Kontrollergebnis der eigenen WG. Rückmeldungen wie „Als erledigt melden“ oder „Rückfrage stellen“ kommen erst in Release 2.

### API

| Methode und Pfad | Recht | Antwort |
| --- | --- | --- |
| `GET /api/student/inspection-results/latest` | Rolle `STUDENT` | `200` mit dem neuesten Ergebnis, `204` ohne sichtbares Ergebnis, `404` ohne eindeutige aktive WG-Zuordnung |

Die WG ergibt sich wie beim Termin aus der aktiven Mitgliedschaft der angemeldeten Person. Maßgeblich ist das Kalenderdatum in `Europe/Berlin`. `validFrom` liegt am heutigen Tag oder früher. `validTo` liegt in der Zukunft oder ist leer. Am Tag `validTo` endet der Zugriff. Ohne aktive Zuordnung oder bei gleichzeitig aktiven Zuordnungen zu verschiedenen WGs liefert die API `404`. Die Daten kommen aus `InspectionResultService.resultsForFlat(flatId, Einzug)` von Team 3 (#77). Sichtbar sind nur Ergebnisse, die ab dem Einzugstag (00:00 Uhr in `Europe/Berlin`) veröffentlicht wurden.

```json
{
  "inspectionId": 7, "kind": "INITIAL", "round": 0, "inspectedAt": "2026-11-18T09:30:00Z", "overallResult": "FAILED",
  "deficiencies": [{ "area": "Küche", "criterion": "Herd & Backofen", "note": "…", "buildingCauseSuspected": false }],
  "resolvedOnSite": [{ "area": "Bad", "criterion": "Dusche & WC", "note": "…" }],
  "passed": [{ "area": "Küche", "criterion": "Arbeitsflächen" }]
}
```

`kind` ist `INITIAL` bei `round` 0, sonst `FOLLOW_UP`. Mängel haben die Bewertung `DEFICIENT`, „Vor Ort geklärt“ `RESOLVED_ON_SITE`; als bestanden zählen `OK` und Kriterien ohne Bewertung. Die Reihenfolge entspricht der Checkliste. Angaben zur prüfenden Person enthält die Antwort bewusst nicht. Bereichs- und Kriteriennamen kommen von Team 3 immer auf Deutsch, auch in der englischen Oberfläche.

### Oberfläche

- Ohne Ergebnis: „Noch kein Ergebnis“. Liefert der Termin-Endpunkt eine angekündigte Kontrolle, steht dort der Hinweis auf die Übermittlung innerhalb von 24 Stunden, sonst „Nach der nächsten Kontrolle erscheint hier euer Ergebnis.“ Der Termin wird nur in diesem Fall geladen.
- Ergebniskarte mit Kontrollart und Datum, Chip „Ergebnis verfügbar“, „Bestanden!“ als grüner Hinweis oder „Nicht bestanden“ in `--warn-text`.
- Nur bei „Nicht bestanden“: Karte „Euer nächster Schritt“.
- Karten „Mängel (n)“ (Chip „Mangel“, bei vermuteter Gebäudeursache stattdessen „Gebäudeursache wird geprüft“ mit Hinweis), „Vor Ort geklärt (n)“ (nur wenn vorhanden) und „Bestandene Kriterien (n)“.

### Entwicklungsdaten

`StudentSampleData` legt für die WG S-01 der Mock-Person `student` eine veröffentlichte Erstkontrolle nach dem Einzug an (nicht bestanden, Mängel bei „Herd & Backofen“ und bei „Dusche & WC“ mit vermuteter Gebäudeursache), sofern das Mock-Konto genau eine aktive Mitgliedschaft in `S-01` der Wohnanlage Sonne hat und seit dem Einzug noch kein veröffentlichtes Ergebnis vorliegt. Die Kontrollen aus `InspectionSampleData` liegen vor dem Einzug und bleiben für Studierende unsichtbar. Weil die Objektbetreuung diese Kontrolle in ihrem Verlauf sieht, sind die erwarteten Zahlen in `InspectionHistoryTests` und `InspectionTests.sampleDataIsIdempotent` angepasst (mit Team 3 abgestimmt).

## Offen

- Der produktive Schreibpfad für `rescheduledAt` gehört zur Anbindung über US-18. Die US-15-Tests setzen gezielt vorbereitete Daten ein. Ein vollständiger End-to-End-Nachweis über Änderung und erneute Ankündigung steht aus.
- Der Reiter „Verlauf“ ist ein Platzhalter.
- Die durchgängige Integrationsprüfung mit US-01 (WG-Beitritt und Austritt) steht aus. Termin- und Ergebnisabfrage verwenden bereits dasselbe Mitgliedschaftsmodell und dieselbe Gültigkeitssemantik. In der lokalen Demo liefert der Dev-Seed die Mitgliedschaft.
- **Kontrollplanung.** Die fachliche Priorisierung gleichrangiger Termine ist noch abzustimmen.
