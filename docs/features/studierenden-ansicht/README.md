# Studierenden-Ansicht: Navigation, Sprache, Checkliste und Ablauf

Feature-Team 2, Sprint 1. Umgesetzte Stories:

| Story | Issue | Stand |
| --- | --- | --- |
| US-X Navigation für Studierenden-Ansicht | #27 | umgesetzt |
| US-26 App-Inhalte auf Englisch oder Deutsch | #28 | umgesetzt |
| US-21 Checkliste je Bereich ansehen | #21 | umgesetzt |
| US-22 Erklärung je Kriterium lesen | #22 | umgesetzt (Anzeige; Pflege durch die Sachbearbeitung folgt mit US-24) |
| US-84 Selbstcheck mit der Checkliste | #24 | offen, braucht die WG-Mitgliedschaft aus US-01 |

## Navigation (US-X)

Der geschützte Einstieg `/student` leitet zur gemeinsamen Startseite `/` weiter. Ohne Sitzung erfolgt zuerst die Anmeldung.

Studierende ohne Personalrolle sehen die App-Ansicht aus dem Wireframe mit den unteren Tabs **Start** (`/`), **Putzplan** (`/student/cleaning-plan`), **Kontrolle** (`/student/inspection/appointment`) und **Mehr** (`/student/more`). Der aktive Tab ist hervorgehoben (`aria-current="page"`). Objektbetreuung und Sachbearbeitung behalten die Kopfnavigation.

- Die Tabs stehen in `frontend/src/app/navigation.ts` (`studentTabs`). Ein Tab kann ein `privilege` tragen und wird dann nur Berechtigten angezeigt.
- Weitere Funktionen kommen nicht als fünfter Tab dazu, sondern unter **Mehr** (`frontend/src/features/more/menu.ts`). Dort stehen nur freigegebene Funktionen, gruppiert wie im Wireframe. Aktuell: *Wohnen → Meine WG* (`/student/flat`) und *Konto und Datenschutz → Einstellungen*.
- `/student/cleaning-plan` zeigt den Putzplan der eigenen WG (US-06/US-07, siehe [Feature-Doku](../putzplan/README.md)).
- `/student/inspection` öffnet den Untertab **Termin**. Die oberen Untertabs **Termin**, **Checkliste**, **Ergebnis** und **Verlauf** stehen als gleich breite Segmente in einer Reihe und gehören zum selben Bereich; jeder hat eine eigene Adresse, sodass Neuladen und „Zurück“ im Browser funktionieren. Alte Adressen wie `/kontrolle` leiten weiter (siehe `frontend/ROUTING.md`). Der untere Tab **Kontrolle** bleibt auf allen vier Seiten aktiv. **Checkliste** setzt `CHECKLIST_READ` voraus. **Ergebnis** zeigt das Kontrollergebnis der eigenen WG (US-38, siehe `docs/features/student-inspections/README.md`), **Verlauf** einen Platzhalter.

## Sprache (US-26)

Studierende wählen die Sprache unter **Mehr → Einstellungen → Sprache** (`/student/settings`). In der Studierenden-Ansicht gibt es deshalb keine Sprachauswahl im Kopfbereich mehr. Die Schaltflächen nutzen die gemeinsame i18next-Instanz (`lib/i18n.ts`): Die Auswahl wird unter `aume.language` gespeichert, gilt sofort für alle Texte und bleibt nach dem Neuladen erhalten. Das Layout lädt nach dem Wechsel die Daten der Seite neu, damit auch Texte aus dem Backend wechseln.

Die Sprachauswahl gibt es nur an dieser einen Stelle (`components/LanguageSwitch.tsx`). Die Checkliste zeigt keinen eigenen Umschalter mehr, sondern folgt der gewählten Sprache.

## Checkliste (US-21, US-22)

`/student/inspection/checklist` zeigt die aktuell veröffentlichte Checkliste nach Bereichen. Es sind dieselben Kriterien, mit denen die Objektbetreuung kontrolliert (`ChecklistService.currentPublished()`). Ein Tipp auf ein Kriterium oder den grünen Pfeil öffnet die Erklärung, ein zweiter Tipp schließt sie wieder (`aria-expanded`). Jeder Bereich hat einen farbigen Kartenkopf. Solange keine Checkliste veröffentlicht ist, erscheint ein Hinweis.

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

## Ablauf und Konsequenzen (US-23)

Unterhalb der Kontrollkarte auf `/student/inspection/appointment` erklärt **Ablauf und Konsequenzen**, wie eine Kontrolle abläuft und was bei Mängeln passiert. Der Abschnitt lädt unabhängig von der Karte und bleibt auch ohne Kontrolltermin erreichbar. Die frühere Adresse `/student/inspection/process` leitet nach der Rechteprüfung zur Abschnittsüberschrift im Reiter **Termin** weiter.

Die Überschrift und die drei aufklappbaren Bereiche stehen in einer gemeinsamen umrandeten Karte. Die Akkordeons behalten ihre farbigen Köpfe, Pfeile und `aria-expanded`.

| Bereich | Feld aus `GET /api/info-pages` | Darstellung |
| --- | --- | --- |
| So läuft die Kontrolle ab | `process` | zugeklappt |
| Was passiert bei Mängeln? | `consequences` | hervorgehoben in weichem Rot (Warnfarben `--warn-*`), Kennzeichnung „Wichtig“, startet aufgeklappt; Sätze mit Kosten (Kosten, €, Gebühr …) sind zusätzlich farbig und fett markiert |
| Sauberkeitsstandards | `standards` | zugeklappt |

Die Texte pflegt die Sachbearbeitung unter **Informationen** (`/standards/info`). Sie sind reiner Text: Jede Zeile wird ein eigener Absatz. Ist ein Text leer, erscheint „Noch keine Informationen.“ Die Texte gibt es nur auf Deutsch. In der englischen Oberfläche steht deshalb ein kurzer Hinweis darüber.

Das Feature liegt in `frontend/src/features/inspection-process/` und hat einen eigenen Abruf der API, ohne Import aus `features/standards`.

### Rechte

| Privileg | Studierende | Objektbetreuung | Sachbearbeitung | Wofür |
| --- | :---: | :---: | :---: | --- |
| `INFO_PAGE_READ` | ✓ | – | ✓ | Texte lesen; die Seite selbst ist zusätzlich auf die Rolle `STUDENT` beschränkt |

Ohne `INFO_PAGE_READ` fehlt der Abschnitt auf der Terminseite, und es erfolgt kein Informationsabruf. Ein direkter Aufruf der alten Adresse führt dann zu „Zugriff nicht erlaubt“.

## Design der Studierenden-App

Schrift **Manrope** (`@fontsource/manrope`, 400–700, eingebunden in `main.tsx`). Die Design-Variablen (`--bg`, `--card`, `--text`, `--primary`, `--area-head`, `--warn-*`, `--radius-card` …) stehen ganz oben in `app/App.css`. Alle Stile der Studierenden-Ansicht gelten nur innerhalb von `.st-wrap`, damit sich die Seiten der Objektbetreuung und Sachbearbeitung nicht verändern.

- Karten weiß mit weichem Schatten statt Rahmen; Hauptbutton `--primary`, Zweitbutton `--primary-soft`.
- Schrift-Hierarchie: Überschriften 17/700, Kriterien 15/600, Zusatztext 13–14/500 in `--muted`, Kontrolldatum 22/700.
- `.icon-tile`: plastische Kachel mit zweifarbigem Icon aus `components/TileIcon.tsx` (Checklisten-Bereiche, „Mehr“). Das Icon eines Bereichs ergibt sich aus seinem Namen (Küche, Bad, Boden, Treppe, sonst Liste).
- Tab-Leiste: weiß, oben abgerundet; aktiver Tab mit Pille und zweifarbigem Icon (`NavIcon`).
- Checklisten-Bereiche haben alle denselben grünen Kopf (`--area-head`).

## Abstimmung mit anderen Teams

- `ChecklistService`, `ChecklistVersionRepository` und `CriterionRepository` sind identisch mit dem Branch `featureteam3@Sprint1/checklist` der Objektbetreuung, damit beide Merges ohne Konflikt zusammenpassen. Wer zuerst nach `develop` merged, liefert die Dateien; der zweite Merge bringt dieselben Inhalte.
- `InspectionSampleData` (Team 3) und `ChecklistSampleData` legen dieselben Kriterien an, jeweils nur wenn noch keine Fassung existiert.
- Die Pflege der Kriterien und ihrer Übersetzungen durch die Sachbearbeitung (US-24) ist noch offen; die Tabelle für Übersetzungen ist dafür vorbereitet.
- Geänderte gemeinsame Dateien: `app/AppLayout.tsx`, `app/navigation.ts`, `app/router.ts`, `app/App.css`, `user/SecurityData.java`.

## Prüfung

- Backend: `ResidentChecklistTests` (Deutsch, Englisch, Rückfall auf Deutsch, nicht veröffentlicht, ohne Anmeldung, ohne Privileg). `ApplicationTests` erwartet im zusammengeführten Stand zehn globale Privilegien, einschließlich `INSPECTION_PLAN` und `INSPECTION_PERIOD_RESPOND`.
- Frontend: `App.test.tsx` prüft Tab-Leiste und aktiven Tab, Kopfnavigation für Personal, Sprachwechsel unter Mehr → Einstellungen samt Speicherung, Checkliste mit Details und Sprachwechsel, leeren Zustand und fehlendes Privileg.
