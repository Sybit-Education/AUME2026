# Kontroll-Anfragen der Objektbetreuung

Die Objektbetreuung beantwortet die Kontrollzeiträume, die ihr die Sachbearbeitung vorschlägt: zustimmen oder einen anderen Zeitraum mit Grund vorschlagen. Umgesetzt ist die Board-Karte #44 (Kontroll-Anfragen beantworten). Das ist die Seite der Objektbetreuung zu US-17 (#35); die Planung der Sachbearbeitung beschreibt [Kontrollplanung](../kontrollplanung/README.md). Im Prozessmodell ist es der Schritt *Zeitraum prüfen* ([kontrollprozess.md](../../projekt/prozessmodell/kontrollprozess.md)).

## Ablauf

1. Die Seite **Kontrollen → Aktuell** (`/inspections`) zeigt zwei Tabellen untereinander: oben **Anfragen**, darunter **Anstehende Kontrollen**. Jede Tabelle zeigt höchstens etwa fünf Zeilen; weitere erreicht man durch Scrollen in der Tabelle, die Kopfzeile bleibt stehen. Sortieren und Filtern funktionieren wie in der [Historie](../kontroll-historie/README.md) (Klick auf die Überschrift, Trichter-Symbol, Chips mit „Zurücksetzen“), hier aber im Browser: Anfragen nach Wohnanlage, Zeitraum und Status; Anstehend nach Wohnanlage, WG, Kontrolle, Zeitraum und Status. Jede Kontrolle steht zu jedem Zeitpunkt in genau einer Tabelle: Solange der Zeitraum abgestimmt wird, unter Anfragen; nach der Ankündigung unter Anstehend; abgeschlossen im Reiter **Historie**. Die Anfragen erscheinen nur mit dem Recht `INSPECTION_PERIOD_RESPOND`. Die frühere Adresse `/inspections/requests` leitet auf `/inspections` weiter.
2. Die Tabelle Anfragen zeigt alle eigenen, noch nicht angekündigten Zeiträume, offene zuerst, sonst nach Beginn. Spalten wie unter Anstehend: Wohnanlage, WGs (Anzahl; ein Zeitraum gilt für alle WGs der Anlage), Kontrolle (*Reguläre Kontrolle*), Zeitraum (darunter die Uhrzeit, nach einem Gegenvorschlag der eigene Vorschlag), Status (*Offen*, *Gegenvorschlag gesendet*, *Abgestimmt*) und bei offenen Anfragen der Knopf **Beantworten**. Ohne Einträge steht dort „Keine Anfragen.“
3. **Beantworten** öffnet den Dialog **Vorschlag · Anlage** mit Zeitraum, Uhrzeit und Zahl der WGs und den Knöpfen *Schließen*, *Anderen Zeitraum vorschlagen* und *Zustimmen*. Nach **Zustimmen** steht der Zeitraum auf `AGREED`, und die Sachbearbeitung kann die Kontrolle ankündigen.
4. **Anderen Zeitraum vorschlagen** öffnet den Dialog **Gegenvorschlag · Anlage**: Zeitraum von/bis (vorbefüllt mit dem Vorschlag) und **Grund** (Pflicht, höchstens 2000 Zeichen). Wie im Wireframe gibt es keine eigene Uhrzeit; es bleibt die vorgeschlagene. Danach steht der Zeitraum auf `COUNTER_PROPOSED`; die Sachbearbeitung nimmt den Gegenvorschlag an oder schickt einen neuen Zeitraum, der wieder als offene Anfrage erscheint.

Welcher Dialog offen ist, steht in der Adresse (`?answer=<id>` bzw. `?counter=<id>`); nach dem Senden zeigt `?saved=agreed|countered` die Bestätigung.

### Fehlerfälle

| Fall | Anzeige |
| --- | --- |
| Zustimmen, obwohl der Zeitraum schon begonnen hat (`start_not_in_future`) | Hinweis, dass nur noch ein Gegenvorschlag geht |
| Gegenvorschlag mit Start nicht in der Zukunft oder Ende vor Start | Grund wie in der Kontrollplanung |
| Grund fehlt | Feld „Grund *“ rot umrandet, darunter „Bitte gib einen Grund an.“; es wird nichts gesendet |
| Zeitraum wurde inzwischen geändert (`period_changed`, Versionskonflikt) | Hinweis, den aktuellen Stand zu prüfen |
| Schon beantwortet oder angekündigt (`invalid_period_status`, `period_announced`) | Hinweis, die Liste ist aktualisiert |
| Zurückgezogen oder einer anderen Person zugeordnet (404) | Hinweis, dass es die Anfrage nicht mehr gibt |

Der Dialog bleibt bei einem Fehler offen; die Eingaben bleiben erhalten.

## Rollen und Daten

| Rolle | Berechtigung | Zugriff |
| --- | --- | --- |
| Objektbetreuung (`CARETAKER`) | `INSPECTION_PERIOD_RESPOND` | nur Zeiträume, bei denen sie als Objektbetreuung eingetragen ist |
| Sachbearbeitung, Studierende | – | 403 |

Keine neuen Tabellen und kein neuer Backend-Code. Genutzt wird die API der Kontrollplanung (Paket `planning`).

## API

| Aufruf | Zweck |
| --- | --- |
| `GET /api/inspection-periods/assigned?size=100` | eigene, noch nicht angekündigte Zeiträume |
| `POST /api/inspection-periods/{id}/agree` | `{version}`, `WAITING_FOR_CARETAKER` → `AGREED` |
| `POST /api/inspection-periods/{id}/counter` | `{startsOn, endsOn, windowStart, windowEnd, reason, version}`, `WAITING_FOR_CARETAKER` → `COUNTER_PROPOSED`; ohne Uhrzeit bleibt die vorgeschlagene |

## Code

`frontend/src/features/inspections/`: `RequestsSection.tsx` (Abschnitt Anfragen und Dialoge, eingebunden in `InspectionsPage.tsx`), `requestsApi.ts`, `requestsActions.ts`, `requests.css`, Route in `routes.tsx`. „Kontrollen“ und „Historie“ sind Reiter der Hauptnavigation (`inspectionNavigation`, `inspectionHistoryNavigation`). Tests: `RequestsSection.test.tsx`.

## Offen

- Die **Overview der Objektbetreuung** (#41, Branch `gruppe_3/overview`) hat einen noch deaktivierten Knopf „Beantworten“. Nach dem Merge verlinkt er auf `/inspections` bzw. `/inspections?answer=<id>`.
- Eine **Vertretung** sieht die Anfragen nicht: Das Backend liefert nur Zeiträume der eingetragenen Objektbetreuung.

## Prüfen

Am schnellsten mit dem Profil `demo` (Backend mit `"-Dspring-boot.run.profiles=dev,demo"` starten, dann als `caretaker` anmelden): Unter **Anfragen** steht S-03 offen, unter **Anstehende Kontrollen** S-05 mit „Kontrolle starten“ und S-04 mit „ab TT.MM.“. Ohne das Profil:

1. Als Sachbearbeitung unter **Kontrollen planen** einen Zeitraum für eine Anlage anlegen und die Objektbetreuung eintragen.
2. Als diese Objektbetreuung unter **Kontrollen**, Tabelle **Anfragen**, zustimmen oder einen Gegenvorschlag senden.
3. Als Sachbearbeitung prüfen, dass der Status auf *Abgestimmt* bzw. *Gegenvorschlag* steht.
