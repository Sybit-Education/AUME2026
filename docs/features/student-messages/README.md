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

Die Zustellung sperrt die konkrete Kontrolle mit der bestehenden Kontrollsperre, prüft den Zustand erneut und speichert die gemeinsame Nachricht und ihre persönlichen Zustellungen in derselben Transaktion. Empfänger sind die zum Zustellzeitpunkt aktiven Mitglieder der WG. Bei einer leeren WG entsteht nur die gemeinsame Nachricht. Eindeutige Schlüssel sichern die Wiederholbarkeit. Die Behandlung offizieller Absagen und das Postfachverhalten bei Wiedereinzug bleiben offen.

## Abweichung vom Arbeitsstand zu US-16 (#20)

Ausschließlich die Ankündigung soll einen PDF-Download anbieten. Beide Erinnerungen führen zur bestehenden interaktiven Checkliste. Das PDF soll beim autorisierten Download aus der referenzierten Fassung erzeugt werden. Die Verfügbarkeit historischer Fassungen ist noch zu klären. Phase 2 erzeugt keine PDF-Dateien und stellt noch keine Postfach-API oder Oberfläche bereit.

## Prüfungen

`InspectionNotificationDeliveryTests` ruft den Zustelldienst mit kontrollierter Uhr und eigenen Daten auf. `InspectionNotificationSchedulerTests` ruft einzelne Scans direkt auf. Beide Tests sind vom automatischen Scheduler-Lauf unabhängig.
