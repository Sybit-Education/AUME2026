# Stand der Kontrollen je Wohnanlage überblicken (US-47)

Die Sachbearbeitung sieht im Tab „Übersicht“ (`/`) eine Zeile je zugeordneter Wohnanlage: letzte abgeschlossene Kontrollrunde mit Kurz-Ergebnis, aktuelle oder nächste Runde mit Status und den nächsten Schritt. Andere Rollen sehen dort weiter das Dashboard. Personen werden nicht angezeigt.

## Bearbeitungsstatus einer Runde

Eine Runde ist ein Kontrollzeitraum (US-17) inklusive der WG-Kontrollen daran. Als „kontrolliert“ zählen Kontrollen mit Status `FINALIZED` oder `PUBLISHED`.

| Status | Anzeige | Bedeutung |
| --- | --- | --- |
| `WAITING_FOR_CARETAKER` | Wartet auf Objektbetreuung | Noch nicht angekündigt |
| `COUNTER_PROPOSED` | Gegenvorschlag der Objektbetreuung | Noch nicht angekündigt |
| `AGREED` | Abgestimmt, nicht angekündigt | Noch nicht angekündigt |
| `ANNOUNCED` | Angekündigt | Angekündigt, noch keine WG kontrolliert |
| `RUNNING` | Läuft · x von y | Mindestens eine WG kontrolliert |
| `COMPLETED` | Abgeschlossen | Alle WGs der Runde kontrolliert |

Auf der Hauptliste:

| Zeilenstatus | Bedeutung | Nächster Schritt |
| --- | --- | --- |
| `NONE` | Noch keine Runde | Kontrolle planen |
| `NO_UPCOMING` | Nur abgeschlossene Runden | Kontrolle planen |
| sonst | Status der aktuellen bzw. nächsten Runde | Gegenvorschlag prüfen, Ankündigen (bis US-18 deaktiviert) oder Details |

„Letzte abgeschlossene Runde“ ist die jüngste Runde mit Status `COMPLETED`. Nachkontrollen und Einzel-WG-Kontrollen ändern diesen Wert nicht. Das Kurz-Ergebnis lautet „x bestanden · y Nachkontrolle“.

Oben stehen Zähler für offene Arbeit: „Gegenvorschlag offen“ und „Abgestimmt, nicht angekündigt“. Ein Klick filtert die Liste. Die Zähler ignorieren die Filter. „Ergebnisse zuzustellen“ kommt mit US-39. Die Seite lädt alle 30 Sekunden und beim Zurückkehren in den Tab neu.

## Detail einer Anlage

`/inspection-overview/<id>` zeigt zuerst die Kontrollrunden (Zeitraum, Kontrollart, Status, Ergebnis). Darunter stehen nur noch offene Nachkontrollen und Einzel-WG-Kontrollen mit WG-Nummer, Zeitraum und Ergebnis „Noch offen“. Abgeschlossene Einzel-WG-Kontrollen gehören in die Historie (US-48).

## Rechte

| Privileg | Studierende | Objektbetreuung | Sachbearbeitung | Wofür |
| --- | :---: | :---: | :---: | --- |
| `CLERK_INSPECTION_OVERVIEW_READ` | – | – | ✓ | Übersicht, Zähler, Detail einer Anlage |

Sichtbar sind nur Anlagen, denen die Person über `staff_assignments` mit `kind = CLERK` zugeordnet ist. Fremde Anlagen liefern `404`.

## API

Alle Pfade beginnen mit `/api/inspection-overview` und sind nur lesend.

| Methode und Pfad | Antwort |
| --- | --- |
| `GET /?state=&kind=&page=` | Paginierte Zeilen `id`, `name`, `lastCompleted`, `current`, `state`. Eine Runde hat `id`, `startsOn`, `endsOn`, `kind`, `state`, `controlled`, `total`, `passed`, `failed`. Unbekannte Filterwerte liefern `400`. |
| `GET /counts` | `counterProposed`, `agreedNotAnnounced` |
| `GET /{residenceId}` | `id`, `name`, `rounds[]`, `openInspections[]` mit `flat`, `kind`, `startsOn`, `endsOn`, `outcome` |

## Entwicklungsdaten

`InspectionOverviewSampleData` ordnet die beiden eindeutig gefundenen historischen Erstkontrollen von S-01 und S-02 einer vergangenen Runde zu. Bei S-01 identifiziert die vorhandene Nachkontrolle die historische Erstkontrolle. Bereits zugeordnete Kontrollen behalten ihren Zeitraum. Die angekündigte US-15-Runde und das veröffentlichte US-38-Ergebnis bleiben erhalten. Egg behält den offenen Gegenvorschlag aus US-17.

Die Verhaltensprüfungen in `InspectionOverviewTests` verwenden eigene transaktionale Fixtures. Ein separater Test prüft das Zusammenspiel der Dev-Seeder und die wiederholte Ausführung des Übersicht-Seeders ohne Duplikate.

## Offen

- „Ankündigen“ ist deaktiviert, bis US-18 einen Endpunkt dafür hat.
- Der Zähler „Ergebnisse zuzustellen“ kommt mit US-39.
- Weitere Kontrollarten erscheinen im Filter, sobald es sie im Zeitraum gibt.
- Historie (US-48) und Quoten (US-49) sind nicht Teil dieser Story.
