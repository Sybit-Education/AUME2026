# Kontrolltermin meiner WG sehen (US-15)

Studierende sehen den angekündigten Kontrollzeitraum ihrer WG auf der Startseite. Bei einem vorhandenen Termin führt „Details“ zur direkt aufrufbaren Detailseite `/student/inspections`.

**Stand: Teilumsetzung.** Issue #18 ist noch nicht vollständig abgeschlossen.

## Regeln

- Das Backend ermittelt die WG aus der aktiven Mitgliedschaft der angemeldeten Person. Eine Mitgliedschaft gilt für die Abfrage ab ihrem Startdatum und solange kein Enddatum gesetzt ist. Eine mitgesendete WG-ID beeinflusst die Auswahl nicht.
- Die Abfrage berücksichtigt geplante Kontrollen der eigenen WG mit angekündigtem Zeitraum in der zugehörigen Wohnanlage. Der letzte Tag zählt noch zum Zeitraum. Von den passenden Terminen wird der mit dem frühesten Beginn angezeigt.
- Ohne genau eine aktive WG oder bei mehreren passenden Terminen mit demselben frühesten Beginn liefert die API keinen Termin.
- Start- und Detailseite verwenden dasselbe lokalisierte Datumsformat. Einzeltage erscheinen einmal, bei einem Jahreswechsel tragen beide Daten das Jahr. Die Anzeige ist unabhängig von der Browserzeitzone.

Die Kontrollkarte steht unter der Begrüßung. Die Willkommenszeile verwendet den Namen aus `GET /api/me`. Der zusätzliche Gruß aus `GET /api/greeting` enthält den gespeicherten Namen und die Wohnanlage. Sobald das Dashboard angezeigt wird, lädt die Karte den Termin unabhängig nach und zeigt währenddessen einen Ladezustand. Ein Sprachwechsel übersetzt die Karte und formatiert den vorhandenen Zeitraum neu.

## Rechte

Die Startseite setzt eine Anmeldung voraus. Die Kontrollkarte und der Terminabruf auf der Startseite sind auf Personen mit der Rolle `STUDENT` beschränkt. Für `/student/inspections` prüft der Routen-Loader dieselbe Rolle. Der Backend-Endpunkt prüft `STUDENT` und beschränkt die Daten unabhängig vom Frontend auf die eigene WG.

## API

`GET /api/student/inspection-appointment` liefert bei einem Treffer `id` (Kontroll-ID), `startsOn` und `endsOn` (jeweils ein Kalenderdatum im Format `YYYY-MM-DD`).

| Ergebnis | Verhalten |
| --- | --- |
| `200 OK` mit `id`, `startsOn` und `endsOn` | Die Karte zeigt den Termin. |
| `204 No Content` | Die Karte zeigt „Keine Kontrolle angekündigt“. |
| `401 Unauthorized` | Die bestehende Sitzungsbehandlung führt zum Login und erhält das Rückkehrziel. |
| `403 Forbidden` | Die Rolle `STUDENT` fehlt. Das Frontend führt zu `/forbidden`. |
| HTTP `500` oder Netzwerkfehler | Auf der Startseite zeigt nur die Kontrollkarte eine übersetzte Fehlermeldung. Begrüßung und Wohnanlage bleiben sichtbar. Auf `/student/inspections` greift das gemeinsame Routenfehlerlayout. |

## Entwicklungsdaten

`StudentSampleData.java` ergänzt im Profil `dev` für das Konto `student` die Mitgliedschaft in `S-01` und den angekündigten Kontrollzeitraum 07.12.–11.12.2026 in „Wohnanlage Sonne“. Nach dem 11.12.2026 liefert dieser feste Beispieltermin den Leerzustand. Für den Termin- und Exportfall stehen automatisierte Tests mit eigenen Testdaten zur Verfügung.

1. Das Backend mit dem Spring-Profil `dev` und das Frontend mit Vite starten. Die Befehle für beide Terminals stehen im [Projekt-README](../../../README.md#lokaler-start).
2. Unter `http://localhost:5173/` über „Studierende“ als Dev-Konto `student` anmelden.
3. Den Termin auf der Startseite prüfen, „Details“ öffnen und auf der Detailseite „In Kalender übernehmen“ wählen. `http://localhost:5173/student/inspections` lässt sich auch direkt öffnen.

## Kalenderexport

Auf `/student/inspections` exportiert „In Kalender übernehmen“ den gesamten Kontrollzeitraum als ganztägiges `.ics`-Ereignis einschließlich des letzten Tages.

## Tests

- Im Verzeichnis `frontend` führt `npm test` die Format- und Kalender-Unit-Tests sowie die React- und Routingtests aus. `InspectionAppointment.test.tsx` prüft unter anderem den Download. `App.test.tsx` prüft Start- und Detailseite, Details-Link, Leerzustand, Rollen und Sitzungsablauf sowie die sichtbare personalisierte Begrüßung bei Terminfehlern.
- Im Verzeichnis `backend` führt `./mvnw verify` beziehungsweise `.\mvnw.cmd verify` unter Windows die Backend-Suite aus. `ApplicationTests` prüft mit MockMvc und In-Memory-H2 unter anderem Zugriffsschutz, Terminauswahl und wiederholtes Seeding.
- `StudentInspectionAppointmentIsolationTests` verwendet eine separate In-Memory-H2-Datenbank (`aume-us15-isolation`). Zwei Studierende in getrennten Sitzungen sehen jeweils nur den Termin ihrer eigenen WG, auch mit fremder WG-ID im Request. Gezielter Aufruf unter Windows: `.\mvnw.cmd "-Dtest=StudentInspectionAppointmentIsolationTests" test`. Unter macOS und Linux lautet der Aufruf `./mvnw -Dtest=StudentInspectionAppointmentIsolationTests test`.

## Offen

- Der Hinweis „Termin geändert“ ist teilweise umgesetzt. Die Karte kann ihn anzeigen, sobald der Terminvertrag ein Änderungssignal liefert. Ein veröffentlichter genauer Kontrolltag ist ebenfalls noch anzubinden.
- Die Einbindung von „Kontrolle“ in die gemeinsame Navigation steht aus. Der Details-Link und der direkte Aufruf stehen bereits zur Verfügung.
- Die Integration mit US-01 (WG-Beitritt) steht aus. Die Terminabfrage verwendet bereits aktive Mitgliedschaften. In der lokalen Demo liefert der Dev-Seed die Mitgliedschaft.
- **Kontrollplanung.** Die fachliche Priorisierung gleichrangiger Termine ist noch abzustimmen.
