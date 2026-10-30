# Rollen und Arbeitsvereinbarung

## Rollen und Verantwortungen

### Product Owner (3)

Entwickeln nicht. Drei Sichten: Studierende, Objektbetreuer, Sachbearbeiter. Je Sicht eine Person.

- Kontakt zu Seezeit für die eigene Sicht
- Stories, Definition of Ready und fachliche Abnahme der eigenen Sicht
- entscheidet den Inhalt der eigenen Sicht
- ein gemeinsames Backlog
- eine Person entscheidet die Priorität, Konflikte zwischen den Sichten und die gemeinsame Definition of Done

### Scrum Master / Coach (1)

- moderiert, räumt Impediments aus
- hält Termine, Deadlines und den organisatorischen Rahmen sichtbar und achtet auf die Einhaltung der vereinbarten Prozesse
- entscheidet nicht über Produkt, Architektur oder den Inhalt der Kontakte
- stimmt bei Uneinigkeit nicht mit

### Entwickler (4 Teams)

#### Integrationsteam (3 Mitglieder)

- Kontakt zu Seezeit-IT und zum Technologiepartner Sybit
  - Anbindung, Zugänge, technische Abstimmung
- Laufzeitumgebungen (Test, Staging, Prod), CI/CD, Tech-Stack und teamübergreifende Architektur
- Sprint 1: Repository, CI, Deploy auf Test
- danach Zugänge, Umgebungen und die teamübergreifende Technik
- führt die automatisierten Prüfungen der Definition of Done aus
- kein Merge nach main, keine Architektur des Schnitts

#### Feature Teams (4, 4, 4)

Alle vier tragen die Feature-Entwicklung.

- Feature-Entwicklung auf Feature Branches
- liefert den Schnitt selbst: Prüfung der Umsetzbarkeit und Umsetzung
- Merge nach main, sobald die Definition of Done erfüllt ist und der Lead Developer ihn technisch trägt
- Lead Developer:
  - entscheidet den technischen Ansatz des Schnitts
  - entscheidet, ob der Merge nach main technisch trägt
  - spricht für das Team mit dem Product Owner und dem Integrationsteam
  - sitzt für das Team in der wöchentlichen Abstimmung

### RACI ab Iteration 1


| Buchstabe | Bedeutung                              |
| --------- | -------------------------------------- |
| R         | führt aus                              |
| A         | entscheidet, genau eine Rolle je Zeile |
| C         | wird gefragt                           |
| I         | wird informiert                        |


Steht in einer Zeile kein R, führt die Rolle mit A die Aufgabe aus.


| Aufgabe                                                      | PO Priorität | PO der Sicht | SM  | Feature-Team der Story | Lead Developer | Integrationsteam |
| ------------------------------------------------------------ | ------------ | ------------ | --- | ---------------------- | -------------- | ---------------- |
| Priorität des gemeinsamen Backlogs                           | A            | C            | I   | I                      | C              | C                |
| Konflikt zwischen den Sichten                                | A            | C            | I   | I                      | I              | I                |
| Definition of Done                                           | A            | C            | I   | C                      | C              | R                |
| Stories einer Sicht                                          | C            | A            | I   | I                      | C              | I                |
| Kontakt zu Seezeit für diese Sicht                           | I            | A            | I   | I                      | I              | I                |
| Produktentscheidung innerhalb der Sicht                      | C            | A            | I   | I                      | C              | I                |
| Definition of Ready                                          | C            | A            | I   | I                      | C              | I                |
| Fachliche Abnahme der erledigten Story nach DoD              | I            | A            | I   | I                      | C              | I                |
| Kontakt zu Seezeit-IT und Sybit                              | I            | I            | I   | I                      | I              | A                |
| Tech-Stack, Umgebungen, CI/CD, teamübergreifende Architektur | C            | I            | I   | I                      | C              | A                |
| Überprüfung der Umsetzbarkeit von Storys nach DoR            | I            | I            | I   | A                      | C              | C                |
| Umsetzung der Stories                                        | I            | I            | I   | A                      | C              | I                |
| Merge nach main                                              | I            | I            | I   | R                      | A              | I                |
| Architektur des eigenen Schnitts                             | C            | I            | I   | C                      | A              | C                |
| Zeitlicher und organisatorischer Rahmen                      | C            | C            | A   | I                      | I              | C                |
| Prozess und Moderation                                       | I            | I            | A   | C                      | C              | I                |


## Arbeitsvereinbarung

### Kommunikation

Mobile und verteilte Arbeit, Kommunikation über Discord.


| Kanal                | Wofür                                                                                                                                                       | Antwortzeit             |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| Entwicklungs-Channel | Laufende Abstimmung aller Feature Teams, des PO und des Scrum Masters zu Stories, Feature Branches, Reviews, Architektur und Terminen                       | höchstens zwei Werktage |
| Feature-Channel      | Jeweils für die Feature Teams intern                                                                                                                        | höchstens zwei Werktage |
| PO-Channel           | Abstimmung der POs, ergibt sich aus den Bedürfnissen der POs, an den drei Sichten orientiert                                                                | höchstens zwei Werktage |
| Emergency-Channel    | Die VM steht, jemand ist nicht erreichbar (zum Beispiel ein wichtiger offener Merge) oder das Prod-Environment ist nicht erreichbar (zum Beispiel HTTP 500) | am selben Tag           |


### Abstimmung

- Jede PO-Person ist die Schnittstelle zu Seezeit für ihre Sicht (Studierende, Objektbetreuer, Sachbearbeiter). Eine davon priorisiert das gemeinsame Backlog.
- Das Integrationsteam ist die Schnittstelle zu Seezeit-IT und zu Sybit.
- Der Scrum Master hält Termine, Deadlines und den organisatorischen Rahmen sichtbar und achtet auf die Einhaltung der vereinbarten Prozesse. Den Inhalt bestimmen die zuständige PO-Person beziehungsweise das Integrationsteam. Die Termine setzen Seezeit, Seezeit-IT, Sybit und die Veranstalter des Projekts an der Hochschule.
- Einmal pro Woche kurze Abstimmung der Feature Teams mit den PO-Personen, dem Integrationsteam und dem Scrum Master (Priorität, Architektur, Blocker). Für das Feature Team sitzt der Lead Developer.

### Uneinigkeit

- Inhalt einer Sicht entscheidet die PO-Person dieser Sicht.
- Priorität und Konflikt zwischen den Sichten entscheidet die priorisierende PO-Person.
- Architektur im Schnitt entscheidet der Lead Developer.
- Teamübergreifende Architektur, Tech-Stack, Umgebungen und CI/CD entscheidet das Integrationsteam.
- Bei Patt über Teamgrenzen entscheidet die gemeinsame Runde.
- Der Scrum Master moderiert und stimmt nicht mit.

## Abschließende Bemerkungen

- Möglicher Übergang zu Sprint 1
  - PO aus den User Teams, danach außerhalb der Entwicklungsteams. SM, Integration und Lead Developer aus dem Core Team.
  - Je Feature Team zwei aus dem User Team und zwei aus dem Core Team.
- Maßstab
  - Neu zusammengesetzte Teams, nicht nur aufgefüllte bestehende.
  - Welche Rolle jemand übernimmt, hängt daran, was die Person kann, will und an Verantwortung übernimmt.

