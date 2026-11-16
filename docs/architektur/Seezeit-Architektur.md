# Seezeit – Architekturübersicht

## 1. Aufgabe und Qualitätsziele

Die Anwendung unterstützt Studierende, Objektbetreuung und Sachbearbeitung bei Vorbereitung, Planung, Durchführung und Veröffentlichung von WG-Kontrollen. Nachkontrollen und private WG-Putzorganisation ergänzen den Ablauf. Ausgangsgröße sind rund 600 WGs in 16 Wohnanlagen laut [Sprint-0-Artefakt](../anforderungen/fachlich/Artefakte_UserTeam_Sprint0.md), Abschnitt 1.

Die [Qualitätsszenarien](../anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md) geben die Prioritäten vor:

| Priorität / Ziel | Konsequenz für die Architektur | Nachweis |
| --- | --- | --- |
| 1 – Zuverlässigkeit | Ergebnisse und Fotos bei Netzabbruch lokal sichern und nach Netzrückkehr synchronisieren | Verbindungsabbruch während der Erfassung; kein Datenverlust, erfolgreicher Abgleich |
| 2 – Benutzbarkeit | Termin, offene Aufgaben und Checkliste direkt zugänglich machen | 90 % finden Termin und Checkliste ohne Hilfe in unter zwei Minuten |
| 3 – Performance | Fotos vor dem Upload komprimieren; Uploadfortschritt und Fehler anzeigen | Maximale Uploadzeit und Testbedingungen vor Abnahme festlegen |
| 4 – Kompatibilität | Responsive Browseroberfläche für Smartphone, Tablet und PC | Kontrollablauf einschließlich Kamera auf vereinbarten Geräten und Browsern prüfen |
| 5 – Datenschutz und Nutzererfahrung | WG- und zuständigkeitsbezogene Zugriffe; Ergebnisse mit Mangel, Foto und Frist verständlich darstellen | Fremdzugriffe abweisen; Ergebnis nach Veröffentlichung für berechtigte Personen sichtbar |
| 6 – Barrierefreiheit und Personalisierung | Screenreader-Zugriff und speicherbare Benachrichtigungseinstellungen berücksichtigen | Bedienelemente mit Screenreader prüfen; Einstellungen bleiben gespeichert |

## 2. Rahmen und Entscheidungsstand

| Thema | Stand |
| --- | --- |
| Dokumentation | Markdown und ADRs in Git, Review über Pull Requests (ADR-0001, akzeptiert) |
| Betrieb | Docker Compose auf einem Linux-Host (ADR-0002, akzeptiert) |
| Backend | Spring Boot als modularer Monolith mit REST-API (ADR-0003, akzeptiert) |
| Frontend | React/TypeScript als responsive Weboberfläche (ADR-0004, akzeptiert; Offlinefrage offen) |
| Daten und Dateien | PostgreSQL und privates Dateivolume (ADR-0005, vorgeschlagen) |
| Anmeldung | Keycloak, gegebenenfalls Seezeit-SSO; Rechteprüfung im Backend (ADR-0006, vorgeschlagen) |
| Historie | Unveränderliche Checklistenfassungen und nachvollziehbare Ergebnisrevisionen (ADR-0007, vorgeschlagen) |
| Hintergrundaufträge | Transaktionale Outbox und separater Worker (ADR-0008, vorgeschlagen) |
| Betriebsabsicherung | Sicherungen, Updates und geprüfte Wiederherstellung (ADR-0009, vorgeschlagen) |
| Produktsicherheit | Zuständigkeiten und Anwendbarkeit gesondert klären (ADR-0010, vorgeschlagen) |

Die technische Zeichnung zeigt auch vorgeschlagene Komponenten. Sie bedeutet keine Freigabe dieser Vorschläge. Hosting in der EU sowie Löschung von Fotos und Historie sind Anforderungen aus den Qualitätsszenarien; konkrete Fristen und Regeln sind noch abzustimmen.

![Kundenansicht der Systemarchitektur](<SystemArchitektur-01 – Kundenansicht.drawio.png>)

## 3. Systemkontext und Anbindungen

| Akteur / System | Aufgabe | Offene Abstimmung |
| --- | --- | --- |
| Studierende | Eigene WG, Termine, Standards, veröffentlichte Ergebnisse und private Aufgaben | Beitritt, Auszug und Zugriff auf frühere Ergebnisse |
| Objektbetreuung | Zugewiesene WGs kontrollieren, Mängel dokumentieren und Gesamtentscheidung treffen | Zuständigkeiten und Bearbeitungsrechte |
| Sachbearbeitung | Stammdaten, Planung, Standards und berechtigte Übersichten | Zugriffsmatrix und Freigabeablauf |
| Seezeit-Identitätsdienst | Mögliche Anmeldung des Personals | Verfügbares System, Protokoll und Kontenpflege |
| Wohnheim-/Bewohnerverwaltung | Wohnanlagen, WGs und zeitlich gültige Mitgliedschaften | Datenhoheit und Schnittstelle; US-54 ist im Sprint-0-Release-Vorschlag später vorgesehen |
| E-Mail-Dienst | Einladungen, Ankündigungen und Hinweise versenden | SMTP-Zugang, Absender, Testzugang und Betreiber |

Das Integrationsteam klärt technische Anbindungen mit Seezeit-IT und Sybit. Die POs klären fachliche Daten und Zugriffsregeln für ihre jeweilige Sicht. Bis freigegebene Schnittstellen vorliegen, werden synthetische Testdaten verwendet. Ein manueller Import ist ein Vorschlag und braucht Validierung sowie eine verantwortliche Rolle.

## 4. Bausteine und gemeinsame Verträge

Die fachlichen Module werden gemeinsam als Backend ausgeliefert. Nachkontrollen, Fotos und WG-Putzorganisation gehören im fachlichen Release-Vorschlag überwiegend zu Release 2; Modulgrenzen bedeuten keine Vorziehung dieser Features. Die folgende Datenverantwortung konkretisiert ADR-0003; einzelne Verträge sind mit den beteiligten Feature-Teams abzustimmen.

| Modul | Daten und Regeln | Vertrag zu anderen Modulen |
| --- | --- | --- |
| Identität und Wohnen | Wohnanlagen, WGs, Mitgliedschaften, Personalzuordnungen | Berechtigungskontext und Zugehörigkeitsprüfung; Passwörter beim Identitätsdienst |
| Standards | Kriterien, Erläuterungen und Checklistenfassungen | Freigegebene Fassung abrufen |
| Kontrollplanung | Zeiträume, Termine und Zuordnungen | Zugewiesene Kontrolle bereitstellen |
| Kontrollen | Entwürfe, Bewertungen, Mängel und menschliche Gesamtentscheidung | Entwurf speichern und Kontrolle abschließen |
| Ergebnisse und Nachkontrollen | Veröffentlichungen, Revisionen, Fristen und Bezug zur ursprünglichen Kontrolle | Ergebnis abrufen, veröffentlichen und korrigieren |
| WG-Organisation | Private Aufgaben, Rotation und Erledigungen | Zugriff ausschließlich für berechtigte WG-Mitglieder als Vorschlag aus ADR-0006; fachliche Bestätigung ausstehend |
| Benachrichtigungen | Versandaufträge, Versuche und Fehler | Fachereignisse verarbeiten |
| Audit und Auswertung | Änderungsereignisse und fachlich freigegebene Auswertungen | Historie und Übersichten ohne private Aufgabendaten |

Jedes Modul schreibt ausschließlich in die eigenen Tabellen. Zugriffe über Modulgrenzen verwenden definierte Schnittstellen. Listen, Einzeldaten, Anhänge und Exporte erhalten dieselben serverseitigen Rechteprüfungen. Einladungen und Mitgliedschaften müssen widerrufbar und zeitlich begrenzt sein.

Als gemeinsame Umsetzungskonventionen werden OpenAPI-Verträge, versionierte Datenbankmigrationen und Prüfungen auf unerlaubte Modulabhängigkeiten vorgeschlagen. Vertragsänderungen stimmt das verantwortliche Feature-Team mit den konsumierenden Teams und bei teamübergreifenden Auswirkungen mit dem Integrationsteam ab.

## 5. Durchgängiger Ablauf: Kontrolle bis Ergebnis

Der erste Release-Vorschlag des User Teams umfasst eine Wohnanlage von der Ankündigung bis zum Ergebnis. Ein Feature-Team liefert seinen Schnitt mit Oberfläche, API, Fachregeln, Migrationen und Tests.

1. Die Objektbetreuung öffnet eine zugewiesene Kontrolle. Das Backend prüft Rolle und Zuständigkeit und liefert die zugehörige Checklistenfassung.
2. Bewertungen und Hinweise werden als Entwurf gespeichert. Gemäß dem Vorschlag ADR-0011 soll die Oberfläche lokalen Speicherstand, Serverbestätigung und ausstehende Synchronisierung getrennt anzeigen; das genaue Offlineverfahren ist noch zu entscheiden.
3. Die prüfende Person legt das Gesamtergebnis selbst fest (US-34). Das Backend prüft Vollständigkeit und erkennt konkurrierende Änderungen.
4. Beim Veröffentlichen entstehen nach dem Vorschlag aus ADR-0007/0008 Ergebnisrevision, Audit-Ereignis und Versandauftrag in derselben Datenbanktransaktion.
5. Der Worker verarbeitet Versandaufträge mit Wiederholungen. Wiederholte Aufrufe dürfen kein zweites fachliches Ergebnis erzeugen. Ein E-Mail-Ausfall verändert das veröffentlichte Ergebnis nicht.
6. Berechtigte WG-Mitglieder sehen das Ergebnis nach Anmeldung. Eine Benachrichtigung enthält einen Verweis auf das geschützte Ergebnis.

Zu prüfen sind falsche WG-Zugehörigkeit, fehlende Personalzuständigkeit, doppelte Veröffentlichungsaufrufe, konkurrierende Entwürfe, Netzabbruch und Versandfehler. Änderungen an aktuellen Standards dürfen historische Kontrollen nicht verändern.

## 6. Deployment und Betrieb

Das Compose-Zielbild umfasst Reverse Proxy, Frontend, Backend, Identitätsdienst, PostgreSQL und Worker; ein Mail-Relay wird bei Bedarf ergänzt. Nur der Proxy stellt die benötigten öffentlichen HTTPS-Zugänge bereit. Datenbank und Dateispeicher bleiben intern. Fotos und Protokolle werden ausschließlich über berechtigungsgeprüfte Backend-Endpunkte geladen.

ADR-0009 schlägt eingeschränkte Containerrechte, geschützte Secrets außerhalb von Git, kontrollierte Image-Versionen, Healthchecks und Überwachung vor. Sicherungen müssen Fachdaten, Identitätsdaten und Anhänge konsistent erfassen und außerhalb des Anwendungshosts liegen. Ein einzelner Host bleibt ein Ausfallpunkt. Die dort genannten Wiederherstellungsziele von 24 Stunden Datenverlust und acht Stunden Wiederanlauf sind unbestätigte Vorschläge.

Das Integrationsteam verantwortet gemäß Rollenvereinbarung Umgebungen und CI/CD. Vor Echtdatenbetrieb sind Betreiber, EU-Standort, Sicherungsplan, Wiederherstellungstest und Wartung nach Projektende zu klären. Releases brauchen abgestimmte Migrationen und einen Wiederherstellungsplan; ein Rückwechsel des Images allein macht eine Schemaänderung nicht rückgängig.

## 7. Zusammenarbeit und offene Entscheidungen

Die [Rollenvereinbarung](../projekt/rollen_arbeitsvereinbarungen/README.md) legt teamübergreifende Architektur und Tech-Stack beim Integrationsteam, den technischen Ansatz eines Schnitts beim jeweiligen Lead Developer fest. Feature-Teams entwickeln durchgängig; die Modulzuständigkeiten werden passend zu den tatsächlichen Teams abgestimmt. Review, Dokumentation und Abnahme richten sich nach [Prozessmodell](../projekt/prozessmodell/Prozessmodell.md) und [DoR/DoD](../anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md).

| Klärung | Zuständige Rollen | Zeitpunkt / Ergebnis |
| --- | --- | --- |
| Git- und Freigabeverantwortung: Prozessmodell nennt Integrationsteam und Release-Branch, Rollenvereinbarung Feature-Team/Lead Developer für Merge nach `main`; DoD verlangt bereits `main` und PO-Abnahme | Integrationsteam, Lead Developer und PO; SM moderiert | Vor erster Integration; gemeinsame Regel in den bestehenden Prozessunterlagen festhalten |
| WG-Beitritt ohne eigenes Konto (US-01) gegenüber Anmeldung und Einladungsverfahren in ADR-0006; außerdem Seezeit-Schnittstellen und Kontenlebenszyklus | Integrationsteam mit Seezeit-IT; PO für fachliche Regeln | Vor Implementierung von Anmeldung und Import; Testzugänge und Zugriffsmatrix |
| Sichtbarkeit privater WG-Aufgaben: ADR-0006 schlägt Ausschluss des Personals vor, Sprint-0-Artefakt führt die Frage noch offen | POs mit Seezeit und Studierenden | Vor Umsetzung der WG-Organisation; fachliche Bestätigung |
| Datenverantwortung, API-Verträge und Modulzuständigkeiten | Integrationsteam und Lead Developer der beteiligten Feature-Teams | Vor paralleler Umsetzung abhängiger Features |
| Checklistenfassungen, Veröffentlichung und Nachkontrollen | PO der jeweiligen Sicht und beteiligte Feature-Teams | Vor Umsetzung; ADR-0007/0008 prüfen |
| Foto-Uploadzeit, Geräte, Löschung und Zugriff nach Auszug | POs mit Seezeit, Integrationsteam und zuständiger Datenschutzstelle | Vor Abnahme bzw. Echtdatenbetrieb; messbare Kriterien und Regeln |
| Betrieb, Wiederherstellung und Wartung nach Projektende | Integrationsteam mit Seezeit-IT und Projektverantwortlichen | Vor Echtdatenbetrieb; Zuständigkeit und Restore-Nachweis |
| Produktsicherheit und CRA-Einordnung | Projektverantwortliche mit zuständiger Fachberatung | Vor Auslieferung; ADR-0010 und vorhandene CRA-Checkliste prüfen |

Architekturvorschläge werden über den [ADR-Workflow](ADR-Workflow.md) entschieden. Diese Übersicht legt weder neue Releasezusagen noch eine zusätzliche Definition of Done fest.
