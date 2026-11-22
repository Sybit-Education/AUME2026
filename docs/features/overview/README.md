# Übersicht für die Objektbetreuung

Issue #41: Als Objektbetreuer möchte ich schnell eine Übersicht über offene Aufgaben und anstehende Kontrollen haben. Aufbau nach dem Mockup im Issue.

## Inhalt

- **Kennzahlen** (die ersten drei führen mit `INSPECTION_PERFORM` zur Kontrollliste `/inspections`)
  - Vorschläge zu beantworten: Kontrollzeiträume, die auf die Antwort der angemeldeten Person warten (je Zeitraum gezählt, nicht je WG)
  - Angekündigte Kontrollen: Erstkontrollen mit angekündigtem Zeitraum
  - Nachkontrollen: offene Kontrollen mit `round` ≥ 1
  - Offene Schäden: noch ohne Datenmodell, wird als „–“ mit dem Hinweis „Fehlt noch: Schadensmeldungen“ angezeigt

Die Übersicht ist ein reines Dashboard. Was auf `develop` noch fehlt, ist sichtbar mit „Fehlt noch“ markiert und hat keine Aktion.
- **Anstehend**: offene Kontrollen (`PLANNED`, `DRAFT`) aller betreuten und vertretenen Wohnanlagen, nach Datum sortiert, höchstens zehn. Je Zeile WG, Wohnanlage, Art (Erst-/Nachkontrolle), Zeitraum oder Frist, Status und Aktion.

| Status | Bedeutung | Aktion |
| --- | --- | --- |
| `PROPOSED` (Vorschlag) | Zeitraum wartet auf die Objektbetreuung (`WAITING_FOR_CARETAKER`) | Für die im Vorschlag eingetragene Person „Beantworten“, deaktiviert mit „Fehlt noch: Beantworten“; Vertretungen sehen „Wartet auf die zuständige Objektbetreuung“ |
| `COUNTER_PROPOSED` (Gegenvorschlag) | Gegenvorschlag gesendet, wartet auf die Sachbearbeitung | Hinweis, keine Aktion |
| `AGREED` (Abgestimmt) | Zugestimmt, noch nicht angekündigt | Hinweis, keine Aktion |
| `ANNOUNCED` (Angekündigt) | Zeitraum angekündigt bzw. Frist einer Nachkontrolle | „Kontrolle starten“ → `/inspections/{id}` |
| `PLANNED` (Geplant) | Erstkontrolle ohne Zeitraum | „Kontrolle starten“ → `/inspections/{id}` |
| `IN_PROGRESS` (Läuft) | Kontrolle im Status `DRAFT` | „Fortsetzen“ → `/inspections/{id}` |

## Rollen und API

| Rolle | Berechtigung | Zugriff |
| --- | --- | --- |
| Objektbetreuung (`CARETAKER`) | `INSPECTION_OVERVIEW_READ` | nur Kontrollen der Anlagen, die sie betreut oder vertritt |
| Studierende, Sachbearbeitung | – | 403 |

`GET /api/overview/caretaker` liefert `{proposals, announced, followUps, openDamages, upcoming[]}`. Jede Zeile in `upcoming` enthält `periodId` und `canRespond`. `openDamages` ist `null`, solange es keine Schadensmeldungen gibt. Die Übersicht liest Kontrollen nur über ein eigenes `OverviewRepository` (Paket `overview`) und ändert keine Daten.

`canRespond` zeigt, dass die angemeldete Person den Vorschlag `periodId` beantworten muss. Die Schnittstelle dafür hat die Kontrollplanung (`POST /api/inspection-periods/{id}/agree` bzw. `/counter`, Privileg `INSPECTION_PERIOD_RESPOND`). Eine Seite zum Beantworten gibt es auf `develop` noch nicht, sie gehört zu #44.

Frontend: Startseite `/` (Reiter „Übersicht“) für alle mit `INSPECTION_OVERVIEW_READ`; `app/HomePage.tsx` wählt je nach Privileg zwischen dieser Übersicht und der allgemeinen Startseite. Seite und Loader liegen in `features/overview`.

## Entwicklungsdaten

`OverviewSampleData` (Profil `dev`, Order 3) legt in der Wohnanlage Sonne die WGs 4.12, 3.08 und 3.10 mit einer angekündigten Erstkontrolle, einer Nachkontrolle und einem Vorschlag an, der auf die Antwort von `caretaker` wartet. Er läuft nur, solange es keine Kontrollen gibt; Startdaten anderer Features haben Vorrang. Anmeldung als `caretaker`.

## Schnittstellen zu anderen Stories

| Abhängigkeit | Story / PR | Stand in der Übersicht |
| --- | --- | --- |
| Kontrolle durchführen, Route `/inspections/:id` | PR #61 (Checkliste) | Gemergt, Links führen auf die Kontrollseite |
| Wohnanlagen-Zuständigkeit über `StaffAssignment` | US-02 #30, PR #47 | Umgesetzt: alle Anlagen mit `CARETAKER`- oder `DEPUTY`-Eintrag, nicht mehr die Anlage aus dem Nutzerprofil |
| Vertretung | #45 | Umgesetzt über `DEPUTY`-Einträge |
| Vorschlag beantworten, Abstimmungsstatus | #44, US-17 #35 (Kontrollplanung) | Zustand aus `InspectionPeriodEntity.status` umgesetzt. Seite zum Beantworten fehlt noch, „Beantworten“ ist deaktiviert und markiert |
| Uhrzeitfenster und Tag je WG | US-17 #35 | Nur Zeitraum bzw. Frist angezeigt |
| Schadensmeldungen | US-14 #12 | Kennzahl „Offene Schäden“ ohne Wert, markiert mit „Fehlt noch“ |
| Stockwerk der WG | US-02 #30 | Feld fehlt in `flats`, wird nicht angezeigt |
| Offline-Modus und Release-Banner aus dem Mockup | #63 | Nicht Teil dieser Story |

## Prüfung

- Backend: `OverviewTests` (Rechte, Kennzahlen und Sortierung, fremde Wohnanlage, Vertretung ohne Beantworten, Zuständigkeit statt Profil-Anlage, Zustimmung und Gegenvorschlag, Startdaten)
- Frontend: `features/overview/dates.test.ts` (Datumsformat), `features/overview/OverviewPage.test.tsx` (Darstellung, Aktionen, Markierung fehlender Funktionen), `app/HomePage.test.tsx` (Startseite je nach Berechtigung)
