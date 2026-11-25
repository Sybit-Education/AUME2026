# Kontrollzeitraum planen und abstimmen (US-17)

Die Sachbearbeitung legt unter `/inspection-planning` für eine ganze Wohnanlage einen Kontrollzeitraum an und schickt ihn der Objektbetreuung zur Abstimmung. Die Objektbetreuung stimmt zu oder schlägt einen anderen Zeitraum mit Begründung vor.

## Ablauf

| Status | Bedeutung | Nächster Schritt |
| --- | --- | --- |
| `WAITING_FOR_CARETAKER` | Vorschlag liegt bei der Objektbetreuung | Zustimmen oder Gegenvorschlag |
| `COUNTER_PROPOSED` | Gegenvorschlag liegt bei der Sachbearbeitung | Annehmen (`AGREED`) oder neuer Zeitraum (`WAITING_FOR_CARETAKER`) |
| `AGREED` | Abgestimmt, noch nicht angekündigt | Ankündigen (US-18) oder ändern (`WAITING_FOR_CARETAKER`) |

„Angekündigt“ ist kein eigener Status: Ein Zeitraum gilt als angekündigt, sobald `announcedAt` gesetzt ist.

## Regeln

- Die Sachbearbeitung plant nur für Anlagen, denen sie über `staff_assignments` mit `kind = CLERK` zugeordnet ist. Fremde Anlagen und Zeiträume liefern `404`.
- Start und Ende sind Pflicht. Das Ende darf nicht vor dem Start liegen, der Start muss in der Zukunft liegen (Zeitzone `Europe/Berlin`).
- Das Uhrzeitfenster ist optional und im Formular mit 09:00 bis 16:00 Uhr vorbelegt.
- Die Objektbetreuung ist aus der Anlage vorbelegt und änderbar. Nur die eingetragene Person darf antworten.
- Beim Absenden entsteht je WG eine Kontrolle mit Status `PLANNED`. WGs mit einer offenen Kontrolle (`PLANNED`, `DRAFT`, `FINALIZED`) werden vorher angezeigt und erhalten keine zweite. Haben alle WGs eine offene Kontrolle, ist Absenden nicht möglich.
- Ändert die Sachbearbeitung einen abgestimmten Zeitraum oder die Person, muss die Objektbetreuung erneut zustimmen.
- Zustimmen ist nur möglich, solange der Start in der Zukunft liegt (sonst `400 start_not_in_future`). Ein Gegenvorschlag bleibt möglich.
- Ein Gegenvorschlag kann auch eine andere Uhrzeit enthalten. Ohne Angabe bleibt die Uhrzeit aus dem Vorschlag.
- Nach der Ankündigung ist der Zeitraum hier nicht mehr änderbar.
- Zurückziehen löscht einen nicht angekündigten Zeitraum und seine geplanten WG-Kontrollen. Die WGs sind danach wieder frei. Wurde eine Kontrolle schon begonnen, antwortet die API mit `409 period_started`.
- `version` schützt vor veralteten Aktionen: Wurde ein Zeitraum inzwischen geändert, antwortet die API mit `409 period_changed`.

## Rechte

| Privileg | Studierende | Objektbetreuung | Sachbearbeitung | Wofür |
| --- | :---: | :---: | :---: | --- |
| `INSPECTION_PLAN` | – | – | ✓ | Seite, Liste, Anlegen, Ändern, Gegenvorschlag annehmen |
| `INSPECTION_PERIOD_RESPOND` | – | ✓ | – | Eigene Vorschläge laden, zustimmen, Gegenvorschlag senden |

## API

Alle Pfade beginnen mit `/api/inspection-periods`.

| Methode und Pfad | Privileg | Body |
| --- | --- | --- |
| `GET /options` | `INSPECTION_PLAN` | Anlagen mit `flatCount`, `caretakerId`, `flatsWithOpenInspection` und Auswahlliste `caretakers` |
| `GET /` | `INSPECTION_PLAN` | Paginierte Liste der Zeiträume |
| `POST /` | `INSPECTION_PLAN` | `residenceId`, `startsOn`, `endsOn`, `windowStart`, `windowEnd`, `caretakerId` |
| `PUT /{id}` | `INSPECTION_PLAN` | `startsOn`, `endsOn`, `windowStart`, `windowEnd`, `caretakerId`, `version` |
| `POST /{id}/accept-counter` | `INSPECTION_PLAN` | `version` |
| `POST /{id}/withdraw` | `INSPECTION_PLAN` | `version`; Antwort `204` |
| `GET /assigned` | `INSPECTION_PERIOD_RESPOND` | Paginierte, noch nicht angekündigte Zeiträume der angemeldeten Person |
| `POST /{id}/agree` | `INSPECTION_PERIOD_RESPOND` | `version` |
| `POST /{id}/counter` | `INSPECTION_PERIOD_RESPOND` | `startsOn`, `endsOn`, `windowStart` und `windowEnd` (optional), `reason`, `version` |

Ein Zeitraum in der Antwort:

```json
{
  "id": 3,
  "residenceId": 1,
  "residenceName": "Wohnanlage Sonne",
  "kind": "REGULAR",
  "status": "COUNTER_PROPOSED",
  "announcedAt": null,
  "startsOn": "2026-12-01",
  "endsOn": "2026-12-03",
  "windowStart": "09:00:00",
  "windowEnd": "16:00:00",
  "caretaker": { "id": 4, "name": "Objektbetreuung" },
  "flatCount": 2,
  "counter": {
    "startsOn": "2026-12-08",
    "endsOn": "2026-12-10",
    "windowStart": "10:00:00",
    "windowEnd": "14:00:00",
    "reason": "Urlaub"
  },
  "version": 1
}
```

Fachliche Fehler kommen als `400` oder `409` mit `{"code": …}`: `dates_required`, `end_before_start`, `start_not_in_future`, `invalid_time_window`, `caretaker_required`, `unknown_caretaker`, `reason_required`, `reason_too_long`, `no_flats_available`, `period_announced`, `period_changed`, `period_started`, `invalid_period_status`.

## Entwicklungsdaten

Im Profil `dev` legt `InspectionPlanningSampleData` für die Anlage Egg einen offenen Gegenvorschlag an. Wohnanlage Sonne bleibt frei, damit dort ein neuer Vorschlag geplant werden kann. Von den Objektbetreuenden hat nur `caretaker` (Sonne) einen Mock-Login.

## Offen

- Die Ansicht der Objektbetreuung zum Zustimmen und für den Gegenvorschlag (Bezug US-19) schließt Gruppe 3 an diese API an. Bis dahin sind diese Schritte nur über die API möglich.
- Der Checklist-Branch muss Kontrollen mit Zeitraum erst nach der Ankündigung starten lassen.
- Weitere Kontrollarten sind nicht Teil dieser Story.
