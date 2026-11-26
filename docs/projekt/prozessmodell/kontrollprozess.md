# Kontrollprozess R1 MVP (BPMN 2.0)

Modell: [kontrollprozess.bpmn](kontrollprozess.bpmn). Öffnen in VS Code mit der Erweiterung „BPMN Editor“ von bpmn-io (`bpmn-io.vs-code-bpmn-io`), im Camunda Modeler oder unter <https://demo.bpmn.io>. Änderungen im Editor speichern direkt in die `.bpmn`-Datei.

Das Modell zeigt den Kontrollzyklus aus Release **R1 MVP** der gemeinsamen User Story Map, Epics E3 bis E6 und E8. Zugang (E1) und WG-Alltag (E2) sind Randprozesse und nicht enthalten. Nachkontrolle und Eskalation (E7) gehören zu R2. Das Modell beschreibt den Zielstand von R1, nicht den aktuellen Umsetzungsstand im Code.

## Aufbau

| Pool / Lane | Aufgaben im R1 |
| --- | --- |
| Pool **WG / Studierende** | Ankündigung erhalten, Termin und Status ansehen, bei Bedarf antworten, vorbereiten, Ergebnis ansehen, bei Bedarf antworten |
| Pool **Seezeit**, Lane **Sachbearbeitung** | Planen, mit der Objektbetreuung abstimmen, ankündigen, Löschung anfordern |
| Pool **Seezeit**, Lane **Objektbetreuung** | Zeitraum prüfen, zustimmen oder Gegenvorschlag senden, Kontrolle durchführen, Ergebnis festlegen, Protokoll erzeugen und an die WG veröffentlichen |

WG und Seezeit kommunizieren nur über Nachrichtenflüsse (gestrichelt): *Ankündigung*, *Absage* und *Protokoll* von Seezeit an die WG, dazu die beiden *Antworten* der WG an Seezeit.

## Ablauf

| Schritt | Wer | Stories |
| --- | --- | --- |
| Reguläre Kontrolle für eine Wohnanlage oder individuelle Kontrolle für einzelne WGs planen | Sachbearbeitung | US-17, US-18b |
| Zeitraum an die Objektbetreuung senden | Sachbearbeitung | US-17 |
| Zeitraum prüfen: Passt er, stimmt die Objektbetreuung zu (*Abgestimmt*), sonst schickt sie einen Gegenvorschlag mit Begründung | Objektbetreuung | US-17 |
| Kontrolle ankündigen, die WG bekommt die *Ankündigung* | Sachbearbeitung | US-18a, US-18b |
| Termin und Status ansehen, Erinnerung 7 Tage und 1 Tag vorher | WG | US-15, US-67, US-16 |
| Bei Fragen oder Abwesenheit im Zeitraum auf die Ankündigung antworten; die Antwort geht an die Sachbearbeitung | WG | #85 |
| Checkliste, Erklärungen, Ablauf und Konsequenzen lesen, Selbstcheck | WG | US-21, US-22, US-23, US-84 |
| Anstehende Kontrollen ansehen | Objektbetreuung | US-19 |
| Kriterien prüfen, bei Mängeln Mangel mit Bereich und Notiz erfassen, Gesamtergebnis festlegen | Objektbetreuung | US-32, US-33, US-34 |
| Protokoll automatisch erzeugen (PDF); das Ergebnis geht direkt an die WG (*Protokoll*) | Objektbetreuung (System) | US-40 |
| Ergebnis und Mängel ansehen | WG | US-38 |
| Bei Fragen zu Mängeln oder Bewertung auf das Ergebnis antworten; die Antwort geht an die Objektbetreuung, CC Sachbearbeitung | WG | #86 |

Als Anmerkungen im Modell: Erinnerungen vor der Kontrolle (US-16), Stand je Wohnanlage in der Übersicht (US-47), Vertretung bei Abwesenheit der Objektbetreuung (#45).

Das Ergebnis veröffentlicht die Objektbetreuung beim Abschließen der Kontrolle direkt; eine Freigabe oder Weiterleitung durch die Sachbearbeitung gibt es nicht 

Voraussetzung, aber kein Schritt pro Kontrolle: Checkliste, Kriterien und die Texte zu Ablauf und Konsequenzen pflegt die Sachbearbeitung vorab (US-24, US-58).

## Abweichungen und Abbrüche im R1

| Fall | Modellierung | Story |
| --- | --- | --- |
| Der Objektbetreuung passt der Zeitraum nicht | Gegenvorschlag mit Begründung. Die Sachbearbeitung prüft ihn und nimmt ihn an (*Abgestimmt*) oder schickt einen neuen Zeitraum. | US-17 |
| Der Zeitraum ändert sich nach der Ankündigung (Ausnahme) | Bedingungsereignis *Ausnahme-Zeitraum muss sich ändern*: neu mit der Objektbetreuung abstimmen und erneut ankündigen. Die WG erfährt den neuen Termin über die erneute *Ankündigung*. Den Hinweis „Termin geändert“ zeigt die App; im Modell gibt es dafür keine eigene Nachricht. | US-17, US-18a, US-15 |
| Die Kontrolle soll entfallen | Bedingungsereignis *Kontrolle soll entfallen*: Löschung mit Begründung anfordern. Gibt die Administration frei, geht eine *Absage* an die WG. Sonst bleibt die Kontrolle bestehen. | US-85 |

Nach der Ankündigung wartet die Sachbearbeitung an einem ereignisbasierten Gateway auf das, was zuerst eintritt: eine nötige Änderung, den Beginn des Kontrollzeitraums oder die Entscheidung, die Kontrolle abzusagen. Die WG wartet entsprechend auf *Absage* oder *Ergebnis verfügbar*.


## Rückkommunikation der WG

Die beiden Antworten gehen als Nachricht an den Pool *Seezeit*: die Antwort auf die Ankündigung (#85) an die Sachbearbeitung, die Antwort auf das Ergebnis (#86) an die Objektbetreuung mit CC an die Sachbearbeitung. Der Ablauf bei Seezeit ändert sich dadurch nicht; die Sachbearbeitung wartet weiter. Folgt aus einer Antwort, zum Beispiel einer Abwesenheit, ein neuer Termin, ist das der Fall *Ausnahme-Zeitraum muss sich ändern*.

## Offene Punkte

1. **Rolle Administration (US-85):** Wer bei Seezeit diese Rolle übernimmt, ist laut Story offen; in der Anwendung gibt es sie noch nicht. Im Modell steckt ihre Entscheidung im Gateway *Administration gibt frei?*.
2. **Niemand da / kein Zutritt:** Abbruch mit neuem Termin oder „nicht bestanden“?
3. **Bearbeitung der Antworten:** Antworten Sachbearbeitung bzw. Objektbetreuung der WG in der App? Bei #86: Wer antwortet, und kann eine Rückfrage das Ergebnis noch ändern?
