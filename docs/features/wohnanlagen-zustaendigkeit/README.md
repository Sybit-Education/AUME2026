# Wohnanlagen, WGs und Zuständigkeit (US-02)

Die Sachbearbeitung sieht ihre Wohnanlagen mit WG-Nummern, Objektbetreuung und Vertretung und kann Betreuung und Vertretung ändern. Stammdaten kommen in R1 nur aus `SampleData`; es gibt keine Eingabemaske für Anlagen, WGs oder Personen.

## Regeln

- Die Sachbearbeitung sieht und ändert nur Anlagen, denen sie über `staff_assignments` mit `kind = CLERK` zugeordnet ist. Fremde Anlagen liefern `404`.
- Andere Sichten sind unverändert: `GET /api/residences` liefert ihnen weiter nur `id` und `name`.
- Betreuung ist Pflicht, Vertretung optional. Beide müssen Nutzer vom Typ `CARETAKER` und verschiedene Personen sein.
- Ändern ersetzt Betreuung und Vertretung vollständig (löschen, dann neu anlegen, damit ein Tausch nicht an der Eindeutigkeit `(user_id, residence_id)` scheitert). Die `CLERK`-Zuordnung bleibt dabei unberührt.

Im Frontend gibt es den Reiter **Wohnanlagen** nur für die Sachbearbeitung. Die Objektbetreuung sieht ihn nicht und bekommt bei `/residences` „Zugriff nicht erlaubt“; ihre Anlagen stehen im Profil (Icon oben rechts, `/profile`). Das Profil gibt es umgekehrt nur für die Objektbetreuung.

## API

| Methode und Pfad | Privileg | Hinweis |
| --- | --- | --- |
| `GET /api/residences` | `RESIDENCE_READ` | Für die Sachbearbeitung zusätzlich `flats` (WG-Nummern), `caretaker`, `deputy` (`null` wenn leer) |
| `GET /api/caretakers` | `RESIDENCE_ASSIGN_STAFF` | Auswahlliste, nur `id` und `name` |
| `PUT /api/residences/{id}/staff` | `RESIDENCE_ASSIGN_STAFF` | Body `{caretakerId, deputyId}`; fehlende `deputyId` entfernt die Vertretung |

Fachliche Fehler kommen als `400` mit `{"code": …}`: `caretaker_required`, `unknown_caretaker`, `deputy_must_differ`. Das Frontend übersetzt die Codes in `features/residences/api.ts`.

## Offen

- Wer die Zuordnung Sachbearbeitung → Anlage in Betrieb pflegt, ist nicht geklärt.
- Die Auswahlliste lädt höchstens 100 Objektbetreuende.
