# Informationen für Bewohnende (US-58)

Die Sachbearbeitung pflegt unter `/standards/info` drei Texte: Ablauf der Kontrolle, Folgen bei Mängeln und Sauberkeitsstandards. Bewohnende lesen dieselben Datensätze über die API. Nach dem Speichern liefert jeder neue Abruf die neue Fassung.

## Rechte

| Privileg | Studierende | Objektbetreuung | Sachbearbeitung | Wofür |
| --- | :---: | :---: | :---: | --- |
| `INFO_PAGE_READ` | ✓ | – | ✓ | Texte über die API lesen |
| `STANDARDS_VIEW` | – | – | ✓ | Seite und Menüpunkt `/standards/info` |
| `INFO_PAGE_EDIT` | – | – | ✓ | Texte speichern |

Aktuell darf die ganze Rolle `CLERK` bearbeiten. Wer `STANDARDS_VIEW`, aber nicht `INFO_PAGE_EDIT` hat, sieht die Texte schreibgeschützt. Rechte pro Person sind für Release 2 vorgesehen. Die Objektbetreuung hat auf diese Funktion keinen Zugriff.

## API

| Methode und Pfad | Privileg | Hinweis |
| --- | --- | --- |
| `GET /api/info-pages` | `INFO_PAGE_READ` | Liefert die drei Texte und `updatedAt` |
| `PUT /api/info-pages` | `INFO_PAGE_EDIT` | Speichert alle drei Texte gemeinsam |

```json
{
  "process": "…",
  "consequences": "…",
  "standards": "…",
  "processEn": "…",
  "consequencesEn": "…",
  "standardsEn": "…",
  "updatedAt": "2026-11-21T10:00:00Z"
}
```

Die englische Fassung liegt in einer eigenen Spalte (`info_pages.body_en`) und kommt in den Feldern `…En`. Sie ist leer, solange keine gespeichert wurde. Auf der Standards-Seite steht sie rechts neben dem deutschen Text. Sie ist dort fest hinterlegt und wird bei jedem Speichern mitgeschickt. Bewohnende sehen aktuell nur Deutsch, die Sprachauswahl baut das Studierenden-Team.

`PUT` sendet dieselben Textfelder ohne `updatedAt`. Die `…En`-Felder sind optional. Fachliche Fehler kommen als `400` mit `{"code": …}`: `info_text_required` oder `info_text_too_long` (mehr als 20.000 Zeichen).

Solange noch nichts gespeichert wurde, sind die Texte leer und `updatedAt` ist `null`. Im Profil `dev` legt `InfoPageSampleData` die Beispieltexte aus dem Wireframe an.

## Anbindung für US-23 (Studierende)

- Die Seite lädt die Texte im Routen-Loader mit `api('/api/info-pages')` und dem Privileg `INFO_PAGE_READ`. Features dürfen nicht aus `features/standards` importieren.
- Die Texte sind reiner Text mit Zeilenumbrüchen. Anzeige mit `white-space: pre-line`, nicht als HTML.
- Leere Texte brauchen einen eigenen Leerzustand.
- Vorgeschlagene Route für die Seite und den Link aus US-18: `/inspection-info`.

## Offen

- Die Anzeige für Bewohnende kommt mit US-23, der Link in der Ankündigung mit US-18.
- Die verbindlichen Texte muss Seezeit liefern.
- Die englische Fassung ist im Frontend fest hinterlegt und nicht bearbeitbar (pflegbar mit US-60).
- Mit der H2-Datenbank im Speicher gehen gespeicherte Texte bei einem Backend-Neustart verloren.
- Speichern zwei Personen gleichzeitig, gilt die zuletzt gespeicherte Fassung.
