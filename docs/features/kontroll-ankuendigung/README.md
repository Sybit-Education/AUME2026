# Kontrolle ankündigen (US-18)

Die Sachbearbeitung kündigt unter `/inspection-planning` eine abgestimmte Kontrolle für alle WGs der Wohnanlage an. Sie kann einen angekündigten Termin auch nachträglich ändern. Die Studierenden-API liefert für neue Zustellungen den Originaltext und die Kennzeichnung der Terminrevision. Die Anzeige im Postfach folgt separat.

## Wie es funktioniert

Die Ankündigung setzt `announcedAt` am Zeitraum und schreibt einen Versandeintrag (`inspection_announcements`). Eine Terminänderung setzt `rescheduledAt`. Nachrichten verschickt dieses Feature nicht selbst. Der Scheduler im Notification-Modul (siehe [In-App-Kontrollnachrichten](../student-messages/README.md)) erkennt die neue Revision innerhalb von etwa 15 Sekunden und stellt jeder WG-Kontrolle des Zeitraums eine Ankündigung zu.

## Regeln

- Ankündigen geht nur bei Status `AGREED`, noch nicht angekündigt und mit Start in der Zukunft.
- Empfänger sind die WGs mit einer Kontrolle in diesem Zeitraum. WGs, die bei der Planung schon eine offene Kontrolle hatten, gehören nicht dazu (siehe [Kontrollplanung](../kontrollplanung/README.md)).
- Der Text wird im Backend aus der Vorlage vorbefüllt: Zeitraum, Uhrzeitfenster und Hinweis auf die Checkliste, mit „ihr“ angesprochen. Die Sachbearbeitung kann ihn ändern. Er ist Pflicht und darf höchstens 5000 Zeichen lang sein.
- Der Anhang ist optional. Der Dialog lädt ihn erst beim Senden über den bestehenden Upload `POST /api/attachments` hoch. Angehängt werden darf nur eine eigene Datei.
- Kanal ist nur „In der App“. E-Mail ist bewusst weggelassen (offene Frage der Story).
- Ein Termin kann nach der Ankündigung ohne neue Zustimmung der Objektbetreuung geändert werden. Der Status bleibt `AGREED`. In der Liste steht „Termin geändert“, in derselben Farbe wie „Angekündigt“, weil für die Sachbearbeitung nichts mehr offen ist. Auch eine reine Uhrzeitänderung setzt `rescheduledAt`, sonst ginge keine Nachricht raus. Der Text für die Terminänderung kommt automatisch aus einer Vorlage.
- Privileg ist `INSPECTION_PLAN`. Nur Zeiträume eigener Anlagen (`staff_assignments`, `CLERK`) sind erreichbar, sonst `404`.

## API

Alle Pfade beginnen mit `/api/inspection-periods/{id}`.

| Methode und Pfad | Body | Antwort |
| --- | --- | --- |
| `GET /announcement-draft` | – | `residenceName`, `flatCount`, `channels`, vorbefüllte `message` |
| `POST /announce` | `message`, `attachmentId`, `attachmentName`, `version` | Versandeintrag |
| `POST /reschedule` | `startsOn`, `endsOn`, `windowStart`, `windowEnd`, `version` | Versandeintrag |
| `GET /announcements` | – | Versandeinträge, neueste zuerst. Die Oberfläche zeigt sie noch nicht an. |

Fehlercodes kommen als `{"code": …}`: `period_announced`, `invalid_period_status`, `period_changed`, `start_not_in_future`, `message_required`, `message_too_long`, `unknown_attachment`, `period_unchanged` sowie die Datums- und Uhrzeitfehler aus der Planung.

## Abhängigkeiten zu anderen Seiten

Diese Seiten wurden nicht geändert. Sie reagieren schon auf `announcedAt` und `rescheduledAt`:

| Bereich | Verhalten nach dem Ankündigen | Offen |
| --- | --- | --- |
| Postfach der Studierenden (Notification-Modul) | Je WG eine Nachricht vom Typ `ANNOUNCEMENT` mit Zeitraum, Uhrzeit und der beim Zustellen veröffentlichten Checklistenfassung. Neue Zustellungen referenzieren den offiziellen Versandeintrag über Zeitraum-ID, `sentAt` und Art, sofern dieser eindeutig ist. Eine Terminänderung erzeugt eine weitere `ANNOUNCEMENT` mit dem neuen Zeitraum und `announcementKind: RESCHEDULE` in der Studierenden-API. | Anzeige des Originaltexts und des Änderungstyps in der Karte sowie ein Download für individuelle Anhänge. |
| Terminkarte der Studierenden (US-15) | Zeigt den Zeitraum, sobald `announcedAt` gesetzt ist, und Änderungen über `rescheduledAt`. | – |
| Objektbetreuung (Kontroll-Anfragen, Kontrollen) | Angekündigte Zeiträume verschwinden aus den Anfragen. Kontrollen lassen sich ab Beginn des Zeitraums starten. | Eine Terminänderung muss sie nicht bestätigen. Sie sieht den neuen Termin bei ihren Kontrollen. |
| Kontrollübersicht der Sachbearbeitung | „Ankündigen“ führt jetzt zu `/inspection-planning?announce=<id>`. | – |

## Offen

- Die Aufbewahrung referenzierter Versandeinträge und ihrer Dateien ist mit der Sachbearbeitung abzustimmen. Für den Originaltext muss der Eintrag unverändert erhalten bleiben. Der geplante individuelle Download setzt zusätzlich den Erhalt der Originaldatei voraus.
- E-Mail als zusätzlicher Kanal. Dafür braucht es einen Outbox-Worker ([ADR-0008](../../architektur/adr/0008-run-background-work-through-a-transactional-outbox.md)).
- Der Upload ist nur im Profil `dev` registriert. Ohne ihn schlägt ein Anhang mit „Der Anhang konnte nicht hochgeladen werden“ fehl. Senden ohne Anhang geht.
- Den Anhang kann nur die hochladende Person herunterladen (`GET /api/attachments/{id}`). Für die WGs fehlt ein Download-Endpunkt.
- Eine Terminänderung, während schon eine WG-Kontrolle läuft, wird nicht verhindert.
