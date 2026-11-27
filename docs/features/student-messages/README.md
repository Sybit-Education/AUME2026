# In-App-Kontrollnachrichten

## Erkennung und Zustellung

Das Notification-Modul startet den ersten Scan nach 15 Sekunden. Zwischen zwei Scans liegt eine feste Pause von 15 Sekunden nach Ende des vorherigen Durchlaufs. Die Planung muss keinen NotificationService aufrufen. Ein Zeitraum wird durch `announcedAt` als offiziell angekündigt erkannt. Für die aktuelle Terminrevision zählt `announcedAt` oder ein neueres `rescheduledAt`.

Bewusste Architekturentscheidung: Der Scheduler verarbeitet den jeweils aktuell gespeicherten offiziellen Kontrollzeitraum. Zwischen zwei Läufen überschriebene Revisionen werden weder rekonstruiert noch nachträglich zugestellt. Bereits erzeugte Nachrichten behalten ihren damaligen Zeitraum. Für den Integrationsbetrieb setzt der Scheduler auf die Revisionsdaten aus US-18. Die produktive Ankündigung durch die Sachbearbeitung ist im Basisstand noch offen.

| Art | Fälligkeit | Checkliste |
| --- | --- | --- |
| Ankündigung | Beim nächsten erfolgreichen Scan für eine gültige, noch offene Kontrolle | Referenz auf die bei Zustellung veröffentlichte Fassung, sofern vorhanden |
| Erinnerung, sieben Tage vorher | Sieben Kalendertage vor dem Beginn ab 00:00 Uhr in `Europe/Berlin` | Link zur interaktiven Checkliste vorgesehen |
| Erinnerung, einen Tag vorher | Einen Kalendertag vor dem Beginn ab 00:00 Uhr in `Europe/Berlin` | Link zur interaktiven Checkliste vorgesehen |

Verpasste Erinnerungen sind nur am jeweiligen Fälligkeitstag nachholbar. Die Berechnung verwendet die aktuelle Revision. Ältere ausstehende Erinnerungen werden nicht erzeugt. Abgelaufene Zeiträume und Kontrollen im Status `FINALIZED` oder `PUBLISHED` werden beim erneuten Lesen ausgeschlossen. Eine bereits beim ersten Start vorhandene gültige Ankündigung ist ein regulärer Kandidat, solange noch keine Nachricht unter ihrem Schlüssel gespeichert ist.

Die Zustellung sperrt die konkrete Kontrolle mit der bestehenden Kontrollsperre, prüft den Zustand erneut und speichert die gemeinsame Nachricht und ihre persönlichen Zustellungen in derselben Transaktion. Empfänger sind die zum Zustellzeitpunkt aktiven Mitglieder der WG. Bei einer leeren WG entsteht nur die gemeinsame Nachricht. Eindeutige Schlüssel sichern die Wiederholbarkeit. Die Behandlung offizieller Absagen bleibt offen.

## Postfach-API

Alle Endpunkte setzen die Rolle `STUDENT` voraus. Sichtbar sind persönliche Kontrollnachrichten der eindeutig aktiven WG. `validFrom` ist inklusive und `validTo` exklusiv, jeweils nach Berliner Kalenderdatum. Bei mehreren gleichzeitig aktiven WGs bleiben Liste und Zähler leer. Ein Austritt beendet den Zugriff. Bei einem späteren Wiedereintritt in dieselbe WG sind die früher persönlich erhaltenen Zustellungen wieder sichtbar.

| Methode und Pfad | Antwort |
| --- | --- |
| `GET /api/student/notifications?page=0&size=20` | `200` mit `content` und `page` (`size`, `number`, `totalElements`, `totalPages`). Offset-Pagination mit Größe 1 bis 100 und fester Reihenfolge nach `createdAt` und Zustell-ID, jeweils absteigend. Ungültige Seitenparameter ergeben `400`. |
| `GET /api/student/notifications/unread-count` | `200` mit `{ "count": 2 }`. Gezählt werden ausschließlich sichtbare Zustellungen ohne `readAt`. |
| `PUT /api/student/notifications/{id}/read` | `204` für sichtbare Zustellungen, einschließlich bereits gelesener. `404` für fehlende, fremde und nicht sichtbare Zustellungen. Der erste Gelesen-Zeitpunkt bleibt bei weiteren Aufrufen erhalten. |
| `GET /api/student/notifications/{id}/checklist.pdf` | `200` mit dem PDF einer sichtbaren Ankündigung und einer veröffentlichten oder zurückgezogenen Fassung. Zusätzlich ist `CHECKLIST_READ` erforderlich. Ohne dieses Recht gilt `403`. Fehlende oder unsichtbare Zustellungen und nicht verfügbare Anhänge ergeben `404`. |

Die GET-Aufrufe ändern den Lesestatus nicht. Die API-Antworten werden mit `Cache-Control: no-store` ausgeliefert. Ohne Sitzung gilt `401`, ohne Rolle `STUDENT` gilt `403`. Schreibende Dev-Sitzungsaufrufe verwenden den bestehenden CSRF-Schutz.

```json
{
  "id": 81,
  "type": "ANNOUNCEMENT",
  "createdAt": "2026-12-01T08:00:15Z",
  "occurredAt": "2026-12-01T08:00:00Z",
  "readAt": null,
  "flatLabel": "S-01",
  "startsOn": "2026-12-07",
  "endsOn": "2026-12-11",
  "windowStart": "09:00",
  "windowEnd": "16:00",
  "hasChecklistPdf": true
}
```

Die ID bezeichnet die persönliche Zustellung. `type` ist `ANNOUNCEMENT`, `REMINDER_7_DAYS` oder `REMINDER_1_DAY`. `occurredAt` ist bei Ankündigungen der offizielle Revisionszeitpunkt und bei Erinnerungen der Erstellungszeitpunkt der Zustellung. Uhrzeiten im Format `HH:mm` sind optional und werden bei fehlender Grenze als `null` ausgegeben.

`hasChecklistPdf` gibt an, ob die aufrufende Person eine referenzierte, veröffentlichte oder zurückgezogene Checklistenfassung dieser Ankündigung herunterladen darf. Der Download prüft die Rechte und die Sichtbarkeit erneut. Er liefert `application/pdf`, `Content-Disposition: attachment`, `Content-Language` und `Cache-Control: no-store`. Der Dateiname lautet `Checkliste_Sauberkeit.pdf` oder `Cleanliness_Checklist.pdf`, abhängig von `Accept-Language`. Fehlende englische Übersetzungen fallen wie in der interaktiven Checkliste auf Deutsch zurück. Ein Download ändert den Lesestatus nicht.

Das A4-PDF wird beim Abruf im Arbeitsspeicher aus der referenzierten Checklistenfassung erzeugt. Es enthält Fassungsnummer, Bereiche und Kriterien mit Beschreibungen und leeren Kästchen. Eine neue Checklistenfassung ändert die Auswahl für einen älteren Anhang nicht. Für reproduzierbare Anhänge müssen historische veröffentlichte Fassungen einschließlich Kriterien und Übersetzungen erhalten und unverändert bleiben. Diese Integrationsvoraussetzung gilt auch für Fassungen mit Status `RETIRED`.

## Nachrichtenansicht

Studierende öffnen das Postfach über die Glocke im gemeinsamen Header oder über **Mehr → Wohnen → Nachrichten** (`/student/messages`). „Mehr“ bleibt aktiv. Die Route verwendet die bestehende Anmeldung und ruft persönliche Kontrollnachrichten seitenweise in der Reihenfolge des Backends ab.

Im geschlossenen Kartenkopf stehen Nachrichtentyp, Datum, Titel und bei ungelesenen Zustellungen „Neu“. Erst beim bewussten Öffnen wird eine ungelesene Nachricht als gelesen markiert. Nach erfolgreichem PUT entfällt „Neu“. Bei einem Fehler lässt sich die Markierung in der geöffneten Karte erneut versuchen.

Ankündigungen zeigen die WG, den Zeitraum und ein vollständiges Zeitfenster. Ist `hasChecklistPdf` gesetzt, bietet die Karte den PDF-Download in der gewählten App-Sprache an. Erinnerungen verlinken mit `CHECKLIST_READ` die interaktive Checkliste.

## Glocke und Ungelesen-Zähler

Die Glocke steht links neben „Abmelden“ im Studierenden-Header. Ein Klick öffnet das Postfach und ändert den Lesestatus keiner Nachricht. Die Anzeige entfällt bei null ungelesenen Nachrichten und zeigt ab 100 den Wert `99+`. Der zugängliche Name enthält die tatsächliche Anzahl in der eingestellten Sprache.

Der Zähler wird beim ersten Rendern des Studierenden-Headers, bei Pfadwechseln, bei Rückkehr in die App und alle 15 Sekunden über die authentifizierte API geladen. Nach einem erfolgreichen PUT zum Markieren startet unmittelbar ein neuer Abruf. Vorübergehende Fehler lassen den letzten erfolgreichen Wert sichtbar. Bei Ende oder Wechsel der Sitzung werden laufende Abrufe, Timer und Listener bereinigt. In Personalansichten und auf der Anmeldeseite finden keine Zählerabrufe statt.

## Abweichung vom Arbeitsstand zu US-16 (#20)

Ausschließlich Ankündigungen bieten einen PDF-Download. Beide Erinnerungen führen zur bestehenden interaktiven Checkliste.

## Prüfungen

`InspectionNotificationDeliveryTests` ruft den Zustelldienst mit kontrollierter Uhr und eigenen Daten auf. `InspectionNotificationSchedulerTests` ruft einzelne Scans direkt auf. Beide Tests sind vom automatischen Scheduler-Lauf unabhängig.

`StudentNotificationTests` prüft die Postfach-API mit eigener H2-Datenbank, kontrollierter Uhr und ersetztem Scheduler. Die Fälle umfassen Pagination, Rollen, WG-Wechsel, Wiedereintritt und paralleles Markieren.

`StudentNotificationPdfTests` prüft Downloadrechte, Dateinamen und Header, Fassungsbindung nach Veröffentlichung einer neuen Checkliste, Lokalisierung und Seitenumbrüche sowie den unveränderten Lesestatus.

`StudentMessagesRoutes.test.tsx` prüft Einstieg, Rollen, Nachrichtenkarten, Seitennavigation, DE/EN-Download und Lesestatus über den App-Router.

`NotificationBell.test.tsx` prüft Header, Rollen, Navigation, zugängliche Zählertexte, Polling, Fehlerbehandlung und Sitzungswechsel. Der Nachrichten-Routingtest prüft zusätzlich den neuen Zählerabruf nach bewusstem Lesen.
