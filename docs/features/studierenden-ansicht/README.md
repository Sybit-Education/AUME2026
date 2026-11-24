# Studierenden-Ansicht: Navigation, Sprache und Checkliste

Feature-Team 2, Sprint 1. Umgesetzte Stories:

| Story | Issue | Stand |
| --- | --- | --- |
| US-X Navigation für Studierenden-Ansicht | #27 | umgesetzt |
| US-26 App-Inhalte auf Englisch oder Deutsch | #28 | umgesetzt |
| US-21 Checkliste je Bereich ansehen | #21 | umgesetzt |
| US-22 Erklärung je Kriterium lesen | #22 | umgesetzt (Anzeige; Pflege durch die Sachbearbeitung folgt mit US-24) |
| US-84 Selbstcheck mit der Checkliste | #24 | offen, braucht die WG-Mitgliedschaft aus US-01 |

## Navigation (US-X)

Studierende ohne Personalrolle sehen die App-Ansicht aus dem Wireframe mit den unteren Tabs **Start** (`/`), **Putzplan** (`/putzplan`), **Kontrolle** (`/kontrolle/termin`) und **Mehr** (`/mehr`). Der aktive Tab ist hervorgehoben (`aria-current="page"`). Objektbetreuung und Sachbearbeitung behalten die Kopfnavigation.

- Die Tabs stehen in `frontend/src/app/navigation.ts` (`studentTabs`). Ein Tab kann ein `privilege` tragen und wird dann nur Berechtigten angezeigt.
- Weitere Funktionen kommen nicht als fünfter Tab dazu, sondern unter **Mehr** (`frontend/src/features/more/menu.ts`). Dort stehen nur freigegebene Funktionen, gruppiert wie im Wireframe. Aktuell: *Konto und Datenschutz → Einstellungen*.
- `/putzplan` zeigt einen Platzhalter („Kommt bald“). Das Putzplan-Feature (US-06/US-07) ersetzt den Eintrag `{ path: 'putzplan', Component: ComingSoonPage }` in `app/router.ts` durch seine eigene Route.
- `/kontrolle` öffnet den Untertab **Termin**. Die oberen Untertabs **Termin**, **Checkliste**, **Ergebnis** und **Verlauf** gehören zum selben Bereich. Der untere Tab **Kontrolle** bleibt auf allen vier Seiten aktiv. **Checkliste** setzt `CHECKLIST_READ` voraus. **Ergebnis** und **Verlauf** zeigen Platzhalter.

## Sprache (US-26)

Studierende wählen die Sprache unter **Mehr → Einstellungen → Sprache**. In der Studierenden-Ansicht gibt es deshalb keine Sprachauswahl im Kopfbereich mehr. Die Schaltflächen nutzen die gemeinsame i18next-Instanz (`lib/i18n.ts`): Die Auswahl wird unter `aume.language` gespeichert, gilt sofort für alle Texte und bleibt nach dem Neuladen erhalten. Das Layout lädt nach dem Wechsel die Daten der Seite neu, damit auch Texte aus dem Backend wechseln.

Die Sprachauswahl gibt es nur an dieser einen Stelle (`components/LanguageSwitch.tsx`). Die Checkliste zeigt keinen eigenen Umschalter mehr, sondern folgt der gewählten Sprache.

## Checkliste (US-21, US-22)

`/kontrolle/checkliste` zeigt die aktuell veröffentlichte Checkliste nach Bereichen. Es sind dieselben Kriterien, mit denen die Objektbetreuung kontrolliert (`ChecklistService.currentPublished()`). Ein Tipp auf ein Kriterium oder den grünen Pfeil öffnet die Erklärung, ein zweiter Tipp schließt sie wieder (`aria-expanded`). Jeder Bereich hat einen farbigen Kartenkopf. Solange keine Checkliste veröffentlicht ist, erscheint ein Hinweis.

### Rechte

| Privileg | Studierende | Objektbetreuung | Sachbearbeitung | Wofür |
| --- | :---: | :---: | :---: | --- |
| `CHECKLIST_READ` | ✓ | – | – | Checkliste über die API lesen |

### API

| Methode und Pfad | Privileg | Antwort |
| --- | --- | --- |
| `GET /api/checklist` | `CHECKLIST_READ` | `200` mit der Checkliste in der Sprache aus `Accept-Language` (Deutsch oder Englisch, Kennzeichnung über `Content-Language`); `404` mit `{"code":"checklist_not_published"}`, solange keine Fassung veröffentlicht ist |

```json
{
  "version": 1,
  "areas": [
    { "name": "Küche", "criteria": [
      { "id": 1, "title": "Arbeitsflächen", "description": "Arbeitsflächen sind frei von Essensresten …" }
    ] }
  ]
}
```

### Übersetzungen

Die Kriterien speichern die deutsche Fassung. Englische Texte stehen in `checklist_criterion_translations` (siehe [Datenmodell](../../architektur/datenmodell.md#standards)). Fehlt eine Übersetzung, liefert die API den deutschen Text. Im Profil `dev` legt `ChecklistSampleData` die Kriterien aus dem Wireframe an, falls noch keine Fassung existiert, und ergänzt die englischen Texte.

## Abstimmung mit anderen Teams

- `ChecklistService`, `ChecklistVersionRepository` und `CriterionRepository` sind identisch mit dem Branch `featureteam3@Sprint1/checklist` der Objektbetreuung, damit beide Merges ohne Konflikt zusammenpassen. Wer zuerst nach `develop` merged, liefert die Dateien; der zweite Merge bringt dieselben Inhalte.
- `InspectionSampleData` (Team 3) und `ChecklistSampleData` legen dieselben Kriterien an, jeweils nur wenn noch keine Fassung existiert.
- Die Pflege der Kriterien und ihrer Übersetzungen durch die Sachbearbeitung (US-24) ist noch offen; die Tabelle für Übersetzungen ist dafür vorbereitet.
- Geänderte gemeinsame Dateien: `app/AppLayout.tsx`, `app/navigation.ts`, `app/router.ts`, `app/App.css`, `user/SecurityData.java`.

## Prüfung

- Backend: `ResidentChecklistTests` (Deutsch, Englisch, Rückfall auf Deutsch, nicht veröffentlicht, ohne Anmeldung, ohne Privileg); Privilegienzahl in `ApplicationTests` auf 8 angepasst (zusammen mit `INSPECTION_PERFORM` aus Team 3).
- Frontend: `App.test.tsx` prüft Tab-Leiste und aktiven Tab, Kopfnavigation für Personal, Sprachwechsel unter Mehr → Einstellungen samt Speicherung, Checkliste mit Details und Sprachwechsel, leeren Zustand und fehlendes Privileg.
