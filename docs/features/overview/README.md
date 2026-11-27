# Übersicht für die Objektbetreuung

Issue #41: Als Objektbetreuer möchte ich schnell eine Übersicht über offene Aufgaben und anstehende Kontrollen haben. Aufbau nach dem Mockup im Issue.

## Inhalt

Die Übersicht zeigt nur, was schon umgesetzt ist. Sie nutzt dieselben Daten und Tabellen wie die Seite Kontrollen (`/inspections`), damit Inhalt und Logik an beiden Stellen gleich sind.

- **Kennzahlen** (jede führt zur Seite Kontrollen `/inspections`)
  - Vorschläge zu beantworten: Anfragen im Status `WAITING_FOR_CARETAKER` (je Zeitraum, nur mit `INSPECTION_PERIOD_RESPOND`)
  - Angekündigte Kontrollen: wie „Angekündigt“ in der Tabelle, also angekündigt und der Zeitraum hat noch nicht begonnen
  - Nachkontrollen: anstehende Kontrollen mit `round` ≥ 1

  Den Status bestimmt für Tabelle und Kachel dieselbe Funktion (`statusOf` in `upcomingStatus.ts`). „Heute“ gilt wie im Backend in Berliner Zeit und wird auf einer offenen Seite jede Minute und beim Zurückkehren neu bestimmt (`useToday`), sodass die Zahlen nach Mitternacht von selbst stimmen.
- **Anfragen**: die Tabelle der [Kontroll-Anfragen](../kontroll-anfragen/README.md) (#44). „Beantworten“ öffnet den Dialog auf der Seite Kontrollen (`/inspections?answer={periodId}`).
- **Anstehende Kontrollen**: die Tabelle „Anstehende Kontrollen“ der Seite Kontrollen mit „Kontrolle starten“ bzw. „Fortsetzen“.

## Rollen und API

| Rolle | Berechtigung | Zugriff |
| --- | --- | --- |
| Objektbetreuung (`CARETAKER`) | `INSPECTION_OVERVIEW_READ`, dazu `INSPECTION_PERFORM` (und für die Anfragen `INSPECTION_PERIOD_RESPOND`) | Übersicht auf der Startseite |
| Studierende, Sachbearbeitung | – | allgemeine Startseite bzw. Kontrollübersicht der Sachbearbeitung |

Kein eigener Backend-Endpunkt: Die Übersicht lädt wie die Seite Kontrollen `GET /api/inspections` und `GET /api/inspection-periods/assigned` (`loadCurrentInspections` in `features/inspections/currentInspections.ts`). Beide Listen werden vollständig geladen, Seite für Seite (`loadAllPages`), damit Kennzahlen und Tabellen nicht bei 100 Einträgen abbrechen. Die Kennzahlen werden im Browser aus diesen Daten gezählt.

Frontend: Startseite `/` (Reiter „Übersicht“) für alle mit `INSPECTION_OVERVIEW_READ`; `app/HomePage.tsx` wählt je nach Privileg zwischen dieser Übersicht und der allgemeinen Startseite. Die Seite liegt in `features/inspections/OverviewPage.tsx`, weil sie die Tabellen dieses Features verwendet (`RequestsSection`, `UpcomingSection`).

## Entwicklungsdaten

`OverviewSampleData` (Profil `dev`, Order 3) legt in der Wohnanlage Sonne die WGs 4.12, 3.08 und 3.10 mit einer angekündigten Erstkontrolle, einer Nachkontrolle und einem Vorschlag an, der auf die Antwort von `caretaker` wartet. Er läuft nur, solange es keine Kontrollen gibt; Startdaten anderer Features haben Vorrang. Anmeldung als `caretaker`.

## Noch nicht enthalten

Bewusst ausgeblendet, bis die Funktionen umgesetzt sind:

| Funktion | Story |
| --- | --- |
| Offene Schäden | US-14 #12 (Schadensmeldungen) |
| Stockwerk der WG | US-02 #30 |
| Offline-Modus und Release-Banner aus dem Mockup | #63 |

## Prüfung

- Frontend: `features/inspections/OverviewPage.test.tsx` (Kennzahlen, Tabellen der Seite Kontrollen, ohne Recht auf Anfragen), `app/HomePage.test.tsx` (Startseite je nach Berechtigung)
