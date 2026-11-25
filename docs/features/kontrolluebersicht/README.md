# Stand der Kontrollen je Wohnanlage überblicken (US-47)

Die Sachbearbeitung sieht im Tab „Übersicht“ (`/`) eine Zeile je zugeordneter Wohnanlage: letzte Kontrolle, anstehende Kontrolle, Bearbeitungsstatus und den nächsten Schritt. Andere Rollen sehen dort weiter das Dashboard. Personen werden nicht angezeigt.

## Bearbeitungsstatus

Der Status kommt aus dem anstehenden Zeitraum (US-17). Anstehend ist der früheste Zeitraum, der noch nicht angekündigt ist oder dessen Ende noch nicht vorbei ist.

| Status | Anzeige | Nächster Schritt |
| --- | --- | --- |
| `NONE` | Noch keine Kontrolle erfasst | Kontrolle planen |
| `NO_UPCOMING` | Keine Kontrolle geplant | Kontrolle planen |
| `WAITING_FOR_CARETAKER` | Wartet auf Objektbetreuung | – |
| `COUNTER_PROPOSED` | Gegenvorschlag der Objektbetreuung | Gegenvorschlag prüfen |
| `AGREED` | Abgestimmt, nicht angekündigt | Ankündigen (deaktiviert bis US-18) |
| `ANNOUNCED` | Angekündigt | – |

Die letzte Kontrolle ist die WG-Kontrolle mit dem jüngsten `inspectedAt`. Als Kontrollart gilt `FOLLOW_UP` bei Nachkontrollen (`round > 0`), sonst die Art des Zeitraums.

Oben stehen zwei Zähler für offene Arbeit: „Gegenvorschlag offen“ und „Abgestimmt, nicht angekündigt“. Ein Klick filtert die Liste. Die Zähler ignorieren die Filter. Die Seite lädt alle 30 Sekunden und beim Zurückkehren in den Tab neu, damit Schritte aus US-17 und US-18 ohne Neuladen sichtbar werden.

Ein Klick auf eine Anlage öffnet `/inspection-overview/<id>`: Zeiträume der ganzen Anlage und Einzel-WG-Kontrollen ohne Zeitraum (mit WG-Nummer), jeweils mit Kontrollart, Zeitraum und Status.

## Rechte

| Privileg | Studierende | Objektbetreuung | Sachbearbeitung | Wofür |
| --- | :---: | :---: | :---: | --- |
| `CLERK_INSPECTION_OVERVIEW_READ` | – | – | ✓ | Übersicht, Zähler, Kontrollen einer Anlage |

Sichtbar sind nur Anlagen, denen die Person über `staff_assignments` mit `kind = CLERK` zugeordnet ist. Fremde Anlagen liefern `404`.

## API

Alle Pfade beginnen mit `/api/inspection-overview` und sind nur lesend.

| Methode und Pfad | Antwort |
| --- | --- |
| `GET /?state=&kind=&page=` | Paginierte Zeilen `id`, `name`, `lastInspection {on, kind}`, `upcoming {id, startsOn, endsOn, kind}`, `state`. Unbekannte Filterwerte liefern `400`. |
| `GET /counts` | `counterProposed`, `agreedNotAnnounced` |
| `GET /{residenceId}?page=` | Paginierte Kontrollen `flat` (nur bei Einzel-WG-Kontrollen), `kind`, `startsOn`, `endsOn`, `status` |

## Entwicklungsdaten

Es gibt keine eigenen Beispieldaten. `clerk` sieht Egg mit dem offenen Gegenvorschlag aus US-17 und Wohnanlage Sonne mit den abgeschlossenen Kontrollen aus der Kontroll-Historie.

## Offen

- „Ankündigen“ ist deaktiviert, bis US-18 einen Endpunkt dafür hat.
- Weitere Kontrollarten erscheinen im Filter, sobald es sie im Zeitraum gibt.
- Historie (US-48) und Quoten (US-49) sind nicht Teil dieser Story.
