# Features in AUME2026 integrieren

Diese Anleitung richtet sich an die Feature-Teams. Sie beschreibt, wo neuer Code hingehört, wie er die vorhandene Anmeldung und Rechteprüfung nutzt und was vor der Integration geprüft werden muss.

Die Einrichtung unter Windows, macOS und Linux sowie die Startbefehle stehen in der [README](README.md). Für die lokale Entwicklung JDK 21, Node.js 24 und das Backend-Profil `dev` verwenden.

## 1. Den fachlichen Schnitt festlegen

Ein Feature umfasst den Weg von der Oberfläche über die API bis zur Speicherung und Rechteprüfung. Vor der Umsetzung mit dem zuständigen PO die Akzeptanzkriterien und mit dem Lead Developer den technischen Ansatz klären. Dazu gehören:

- Welche der drei Sichten nutzt die Funktion: Studierende, Objektbetreuung oder Sachbearbeitung?
- Welche Aktionen sind erlaubt, und auf welche Wohnanlage beziehungsweise WG sind sie begrenzt?
- Welche Daten, Schnittstellen und Features anderer Teams werden benötigt?
- Welche Erfolgs-, Fehler- und Zugriffsfälle müssen nachweisbar funktionieren?

Die [Definition of Ready und Definition of Done](docs/anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md) und die [Rollenvereinbarung](docs/projekt/rollen_arbeitsvereinbarungen/README.md) bilden dafür die Grundlage. Fachliche Annahmen und Abhängigkeiten in der Story festhalten.

## 2. Feature-Branch und Zusammenarbeit

Auf dem mit dem Team abgestimmten Integrationsstand einen kurzlebigen Feature-Branch anlegen. Das [Prozessmodell](docs/projekt/prozessmodell/Prozessmodell.md) sieht das Muster `<Gruppenkürzel>/<Feature-Bezeichnung>` vor, zum Beispiel:

```bash
git switch -c gruppe_1/kontrollplanung
```

Änderungen in fachlich zusammenhängenden Commits halten und früh einen Pull Request für Feedback öffnen. Gemeinsame Dateien wie `App.tsx`, `SecurityData.java`, `SecurityConfig.java`, Abhängigkeiten und Infrastruktur mit betroffenen Teams abstimmen. Neue Funktionen möglichst in eigenen Dateien und Fachpaketen ergänzen.

**Offene Prozessfrage:** Das Prozessmodell beschreibt den Weg über `develop` und Release-Branches nach `main`; die Rollenvereinbarung nennt einen Merge nach `main` durch das Feature-Team mit technischer Verantwortung des Lead Developers. Zielbranch und Merge-Verantwortung deshalb mit Lead Developer und Integrationsteam anhand der aktuellen Teamabsprache festlegen. Diese Anleitung löst den Widerspruch nicht durch eine neue Regel. Änderungen über einen geprüften Pull Request integrieren.

## 3. Orientierung im Grundgerüst

| Bereich | Vorhandene Aufgabe | Einstieg für ein Feature |
| --- | --- | --- |
| `backend/src/main/java/de/seezeit/aume/` | Spring-Boot-Anwendung und Fachpakete | Neues Fachpaket unterhalb dieses Basispakets |
| `backend/.../auth/` | Anmeldung, aktueller Nutzer, gemeinsame Authority-Abbildung | Bestehende Authentifizierung nutzen |
| `backend/.../user/` | Nutzer, Rollen, Privilegien und Startdaten | Benötigte Berechtigungen und Beispieldaten ergänzen |
| `backend/.../residence/` | Wohnanlagen, DTO-Endpunkt mit Zugriff auf die eigene Wohnanlage | Beispiel für eine fachlich begrenzte Abfrage |
| `backend/.../api/config/OpenApiConfig.java` | Gemeinsame OpenAPI-Metadaten und Authentifizierungsschema | API-Vertrag prüfen; gemeinsame Metadaten nur bei Bedarf ändern |
| `backend/src/test/java/de/seezeit/aume/` | Backend- und Sicherheitstests | Tests im zum Fachpaket passenden Testpaket |
| `frontend/src/App.tsx` und `frontend/src/app/` | Router, gemeinsames Layout und Seitenguards | Feature-Routen und Navigation registrieren |
| `frontend/src/auth/` | Gemeinsame Sitzung, React-Kontext und Rückkehr nach Login | `useAuth()`, `protectedLoader()` und gemeinsame Antwortprüfung nutzen |
| `frontend/src/auth.ts` | Gemeinsamer API-Zugriff und Anmeldeablauf | Für geschützte Requests `api()` verwenden |
| `infrastructure/` | Compose-Konfiguration und Umgebungsvariablen-Vorlage | Neue Dienste oder Konfiguration bei Bedarf ergänzen |
| `docs/` | Anforderungen, Architektur und Teamprozess | Feature und relevante Entscheidungen dokumentieren |

`backend/.../` steht in der Tabelle für `backend/src/main/java/de/seezeit/aume/`.

Das Backend folgt dem akzeptierten [ADR-0003 zum modularen Monolithen](docs/architektur/adr/0003-structure-the-backend-as-a-modular-monolith.md). Aktuell sind nur wenige Fachpakete umgesetzt. Neue Pakete nach fachlicher Zuständigkeit schneiden, beispielsweise `inspection` für Kontrollen. Ein Modul stellt anderen Modulen eine gezielte Service-Schnittstelle bereit. Änderungen an fremden Moduldaten erfolgen über diese Schnittstelle; keine direkten Schreibzugriffe auf deren Repositories.

## 4. Backend-Funktion ergänzen

Als Beispiel dient im Folgenden eine neue Kontrollübersicht. Paket `inspection`, Endpunkt `/api/inspections` und Privileg `INSPECTION_READ` sind **Beispiele für eine Erweiterung und noch nicht vorhanden**.

Eine mögliche Aufteilung innerhalb des neuen Fachpakets:

```text
inspection/
  InspectionEntity.java
  InspectionRepository.java
  InspectionService.java
  InspectionController.java
  InspectionView.java
```

Die Aufteilung an den Umfang anpassen. Der vorhandene `ResidenceController` enthält für seinen kleinen Lesefall die Abfrage direkt; bei Geschäftsregeln oder mehreren Aktionen diese in einen Service verschieben.

### Daten und Geschäftsregeln

- JPA-Entities bilden die gespeicherten Fachdaten ab; Repositories kapseln die Datenbankabfragen.
- Eingaben und Antworten als eigene DTOs modellieren. JPA-Entities und ihre Beziehungen nicht unmittelbar als API-Antwort ausgeben.
- Geschäftsregeln und zusammengehörige Schreiboperationen im Service abbilden. Die Transaktion muss auch die Prüfung der Zuständigkeit und die Änderung umfassen.
- Lesende Services mit `@Transactional(readOnly = true)`, schreibende mit `@Transactional` versehen. Bei `open-in-view: false` benötigte Beziehungen innerhalb der Transaktion laden und dort in DTOs umwandeln.
- Pflichtfelder, Wertebereiche, erlaubte Zustandswechsel und widersprüchliche Daten serverseitig prüfen. Falls Bean Validation mit `@Valid` und Jakarta-Constraints eingeführt wird, auch die benötigte Validation-Abhängigkeit im Backend ergänzen; sie ist derzeit nicht explizit eingerichtet.

### API-Vertrag und Rechteprüfung

Neue Endpunkte unter `/api` anlegen. Listen wie beim [ResidenceController](backend/src/main/java/de/seezeit/aume/residence/ResidenceController.java) paginieren: `Pageable` entgegennehmen, DTOs abbilden und `PagedModel` mit `content` und `page` zurückgeben. Das vorhandene Limit liegt bei 100 Einträgen pro Seite.

Eine Aktionsberechtigung mit Method Security prüfen, beispielsweise:

```java
@PreAuthorize("hasAuthority('INSPECTION_READ')")
```

Das ist eine Annotation für die neue Controller- oder Service-Methode, keine vollständige Implementierung. Für ein neues Feature unter `/api/**` ist normalerweise keine neue Filterkette nötig: Die vorhandene [SecurityConfig](backend/src/main/java/de/seezeit/aume/auth/SecurityConfig.java) fordert dort bereits eine Anmeldung. Die fachliche Berechtigung muss das Feature zusätzlich prüfen.

Den aktuellen Nutzer aus dem verifizierten `Principal` ermitteln, wie im bestehenden Wohnanlagen-Endpunkt:

```java
var user = users.requireUser(principal.getName());
```

`users` ist dabei ein per Konstruktor injizierter `AppUserService`. Nutzerkennungen aus Request-Headern oder dem Request-Body ersetzen diesen Principal nicht.

**Die Berechtigung für eine Aktion ersetzt nicht die Prüfung des konkreten Datensatzes.** Eine Kontrolle muss zur erlaubten Wohnanlage beziehungsweise WG gehören. Abfragen möglichst bereits auf den zugänglichen Datenbereich einschränken. Auch bei Detail-, Änderungs-, Lösch- und Export-Endpunkten verhindern, dass fremde IDs den Zugriff erweitern. Bei Neuanlagen Zuordnungen serverseitig bestimmen oder prüfen. Das aktuelle Nutzermodell enthält eine Wohnanlage; WG-Mitgliedschaften und zeitliche Gültigkeiten sind noch nicht implementiert und müssen bei entsprechenden Features ergänzt werden.

HTTP-Verhalten im API-Vertrag festlegen: beispielsweise `400` für ungültige Eingaben, `401` für fehlende Anmeldung, `403` für fehlende Rechte, `404` für nicht zugängliche beziehungsweise nicht vorhandene Ressourcen und `409` für fachliche Konflikte. Die vereinbarten Fälle in Tests absichern. Neue Endpunkte in der generierten [Swagger UI](http://localhost:8080/swagger-ui.html) prüfen; DTOs, Parameter und relevante Antworten bei Bedarf mit OpenAPI-Annotationen erläutern.

Repositories bleiben interne Persistenzschnittstellen. Spring Data REST ist im Grundgerüst nicht aktiv; neue Repositories nicht als unkontrollierte CRUD-API veröffentlichen.

## 5. Rollen, Privilegien und Entwicklungsdaten erweitern

Die Rollen `STUDENT`, `CARETAKER` und `CLERK` bündeln Aktionsberechtigungen. `AppUserService` bildet sie gemeinsam mit ihren Privilegien auf Spring Authorities ab. Die drei Mock-Nutzer verwenden dieselbe Abbildung wie die Keycloak-Anmeldung.

Für die beispielhafte Kontrollübersicht:

1. Mit dem PO festlegen, welche Rollen Kontrollen sehen dürfen und auf welche Daten der Zugriff begrenzt ist.
2. In [SecurityData.java](backend/src/main/java/de/seezeit/aume/user/SecurityData.java) das Privileg `INSPECTION_READ` über den vorhandenen Helfer `privilege(...)` anlegen.
3. Die bereits vorhandenen `role(...)`-Aufrufe der berechtigten Rollen um dieses Privileg ergänzen. Bestehende Berechtigungen beibehalten. Keinen zweiten Aufruf für dieselbe Rolle hinter den ersten setzen.
4. Controller beziehungsweise Service mit `hasAuthority('INSPECTION_READ')` schützen und die Datenzuständigkeit prüfen.
5. In [SampleData.java](backend/src/main/java/de/seezeit/aume/SampleData.java) oder einem eigenen, ebenfalls auf `dev` begrenzten Seeder synthetische Fachdaten ergänzen. Benötigte Rollen und Nutzer müssen zuvor angelegt sein; die Reihenfolge der vorhandenen Seeder berücksichtigen.
6. Backend neu starten und Mock-Nutzer erneut anmelden. Erlaubte und verbotene Fälle prüfen.

**Besonderheit der Seeder:** `SecurityData.role(...)` erstellt nur fehlende Rollen. Bei bereits vorhandenen Rollen werden geänderte Privilegien-Sets nicht übernommen. In der derzeitigen H2-Datenbank mit `ddl-auto: create-drop` wirkt die geänderte Startzuordnung nach einem Backend-Neustart, weil die Datenbank neu aufgebaut wird. Für eine dauerhaft gespeicherte Datenbank braucht eine solche Änderung einen gezielten Datenänderungs- oder Migrationsschritt. Die Zuordnungen nicht bei jedem Start pauschal überschreiben.

Die drei Mock-Nutzer `student`, `caretaker` und `clerk` für die gemeinsame Entwicklungsbasis erhalten. Ihr Login ist auf genau diese Kennungen beschränkt; ein zusätzlich gespeicherter Nutzer bekommt dadurch noch keine Mock-Anmeldung. Weitere Zugriffskonstellationen lassen sich gezielt in Tests aufbauen. Keine echten Personen- oder Zugangsdaten als Beispieldaten einchecken.

Für ein Feature keine eigene Anmeldung, Token-Verarbeitung oder globale Abschaltung von CSRF einführen. Außerhalb von `dev` wird die Identität über Keycloak verifiziert; Anwendungsrollen bleiben in der Datenbank. Änderungen an diesem Zusammenspiel betreffen die gemeinsame Architektur.

## 6. Frontend anbinden

Das Frontend verwendet React Router im Data Mode. Die vollständige Anleitung mit Routen-, Loader- und Navigationsbeispielen steht in [frontend/ROUTING.md](frontend/ROUTING.md). Als umgesetzte Referenz dient `frontend/src/features/residences/`.

Neue Features bündeln Seiten, Typen, API-Funktionen und ein eigenes Routen-Array unter `frontend/src/features/<feature>/`. Das Array in `app/router.ts` innerhalb der geschützten `RequireAuth`-Gruppe ergänzen. Einen optionalen Menüeintrag in `app/navigation.ts` registrieren; Labels und Seitentexte in beiden Sprachdateien anlegen. `App.tsx` verbindet nur Authentifizierungs- und Router-Provider.

Für geschützte Datenabfragen `protectedLoader()` aus `app/loaders.ts` verwenden. Die Seite zusätzlich mit `RequirePrivilege` schützen, falls sie ein Privileg benötigt. Parent- und Child-Loader können parallel laufen; ein Seitenguard allein verhindert deshalb keine unberechtigte Datenabfrage. Der Loader muss seine Sitzung und Berechtigung vor dem API-Aufruf prüfen. Die Navigation wird anhand von Privilegien gefiltert. Die verbindliche Autorisierung und Prüfung konkreter Datensätze bleibt im Backend.

API-Funktionen verwenden den gemeinsamen Helfer `api()` aus `src/auth.ts` und prüfen Antworten mit `requireOk()` aus `src/auth/response.ts`. Dadurch werden `401`, `403` und übersetzbare Fehler einheitlich behandelt. Relative `/api/...`-URLs und das `request.signal` des Loaders verwenden. DTO-Typen müssen zum Backend-Vertrag passen; TypeScript-Casts validieren keine Antwortdaten. Neue schreibende Actions müssen ihre Rechte selbst prüfen; der Schutz des Leseloaders reicht dafür nicht aus.

Die aktuelle Sitzung kommt aus `useAuth()`; neue Seiten laden keinen eigenen Anmeldestatus. `RequireAuth` erhält den angeforderten Pfad für die Rückkehr nach der Anmeldung. `Link`, `NavLink` und Router-Navigation statt vollständiger Browser-Neuladungen für interne Seiten verwenden. Für Filter und Pagination `useSearchParams()` nutzen. Die Wohnanlagen-Seite zeigt die Verwendung von `useLoaderData()` und paginierten Daten.

Ladezustand, leere Ergebnisse, Fehler und fehlende Rechte darstellen. Das gemeinsame Layout bietet Navigation, Sprachwahl, Logout und Ladeanzeige. Nach dem Sprachwechsel werden aktuelle Loader revalidiert. Zusätzliche Abfragen außerhalb der Loader müssen diesen Wechsel selbst behandeln. Beschriftungen, Tastaturbedienung und verständliche Rückmeldungen bei der Umsetzung mitprüfen.

### Texte und Lokalisierung

Alle neuen Bedientexte, Beschriftungen, Ladezustände und Meldungen in `frontend/src/locales/de.json` und `frontend/src/locales/en.json` ergänzen. Fachliche Schlüssel nach Funktion gruppieren. In Komponenten `useTranslation()` aus `react-i18next` verwenden und beispielsweise `t('auth.login')` aufrufen. Die deutschen Schlüssel werden über `src/i18next.d.ts` für TypeScript typisiert; keine Schlüssel mit Typ-Casts an der Prüfung vorbeiführen.

Dynamische Werte über benannte Platzhalter einsetzen, beispielsweise einen neuen Text `"created": "Kontrolle {{id}} wurde angelegt."` mit `t('inspections.created', { id })`. Den Schlüssel vorher in beiden Sprachen ergänzen. Sätze nicht aus übersetzten Fragmenten zusammensetzen. Für Mengen die i18next-Pluralformen (`_one`, `_other`) mit `count` verwenden; Datums- und Zahlenwerte mit `Intl.DateTimeFormat` beziehungsweise `Intl.NumberFormat` und der aktuellen Sprache formatieren. Die fachlich maßgebliche Zeitzone gesondert festlegen.

Fehlerzustände als Schlüssel halten, damit bereits sichtbare Meldungen beim Sprachwechsel mitwechseln. Für API-Funktionen und andere Helfer `UiError` aus `src/errors.ts` verwenden; `errorKey(...)` ordnet unbekannte Fehler einem kontrollierten Fallback zu. Technische Fehlermeldungen oder Stacktraces nicht direkt als UI-Text anzeigen. Bei zusätzlichen Fehlern einen Eintrag unter `errors` in beiden JSON-Dateien anlegen.

Die gemeinsame Initialisierung in `src/i18n.ts` verwenden. Sie behandelt gespeicherte Auswahl, Browsersprachen, deutschen Fallback, HTML-`lang` und blockierten Browserspeicher. Komponenten sollen keine eigene Spracherkennung oder Speicherung einführen. Die Namen der Sprachen werden in der Auswahl als Eigenbezeichnungen (`Deutsch`, `English`) angezeigt.

Das Backend bekommt die Sprache über `Accept-Language` vom gemeinsamen `api()`-Helfer. Für vom Server formulierte Texte die vorhandene `MessageSource` und die auf Deutsch/Englisch begrenzte `LocaleConfig` nutzen. Deutsche Standardtexte stehen in `messages.properties`, englische in `messages_en.properties`. Beispiel ist der `GreetingController`; Parameter werden dort über `{0}`, `{1}` eingesetzt, unabhängig von der `{{name}}`-Syntax des Frontends. Sprachabhängige Antworten mit `Content-Language` kennzeichnen. Maschinenlesbare IDs, Statuswerte, Authorities und gespeicherte Nutzerdaten bleiben sprachunabhängig. Neue Fachfehler möglichst über stabile Fehlercodes an die Oberfläche übergeben, die sie in ihre eigenen Texte übersetzt.

Der aktuelle Sprachwechsel revalidiert die aktiven Routen und lädt damit auch den lokalisierten Gruß neu. Neue serverseitig lokalisierte Ansichten sollen ihre Daten über Routen-Loader beziehen; zusätzliche Abfragen und Caches außerhalb der Loader müssen die Sprache selbst berücksichtigen. Neue Sprachen erfordern zusätzlich die Ressourcen, unterstützten Sprachen und Auswahl im Frontend sowie bei Backend-Texten die Locale-Konfiguration und Message-Bundles.

Bei neuen Texten die Schlüssel in beiden Sprachen sowie Platzhalter und Pluralformen prüfen. Mindestens einen relevanten Sprachwechsel in den Feature-Tests und im Browser berücksichtigen. Nutzer- und Wohnanlagennamen sind Fachdaten und werden nicht durch Übersetzungen ersetzt.

## 7. Datenbank und Konfiguration

Aktuell verwendet das Backend eine H2-Datenbank im Speicher. `ddl-auto: create-drop` baut das Schema beim Start auf; Entwicklungsdaten gehen beim Neustart verloren. Das ist keine Migrationsstrategie für einen dauerhaften Betrieb. PostgreSQL und weitere Architekturbausteine sind in den ADRs beschrieben; deren Status und tatsächlichen Implementierungsstand vor einer Erweiterung prüfen.

Neue Konfiguration mit nachvollziehbaren Namen dokumentieren. Falls Umgebungsvariablen oder Dienste hinzukommen, auch `infrastructure/.env.example`, die betroffene Compose-Konfiguration und die README ergänzen. Eine `.env` wird beim direkten Maven-Start nicht automatisch als Spring-Konfiguration geladen; erforderliche Werte müssen im Prozess beziehungsweise in der IDE gesetzt werden. Zugangsdaten und lokale Pfade gehören nicht in versionierte Konfigurationen.

Neue Abhängigkeiten im jeweiligen Projekt ergänzen: Backend in `pom.xml`, Frontend mit npm inklusive aktualisiertem `package-lock.json`. Die gemeinsame Build- und Deployment-Konfiguration bei Änderungen mitprüfen.

## 8. Feature überprüfen

Backend-Tests im passenden Fachpaket unter `backend/src/test/java/de/seezeit/aume/` ergänzen. Für die Integration von Anmeldung und Rechten dienen [ApplicationTests](backend/src/test/java/de/seezeit/aume/ApplicationTests.java), [ProductionSecurityTests](backend/src/test/java/de/seezeit/aume/ProductionSecurityTests.java) und [JwtValidationTests](backend/src/test/java/de/seezeit/aume/auth/JwtValidationTests.java) als Referenz.

Mindestens die für das Feature relevanten Fälle absichern:

| Fall | Erwartung |
| --- | --- |
| Berechtigter Nutzer mit gültigen Daten | Fachlich korrektes Ergebnis und korrekte Speicherung |
| Nicht angemeldeter Nutzer | Geschützter Zugriff wird abgewiesen |
| Angemeldeter Nutzer ohne Privileg | Aktion wird abgewiesen |
| Berechtigter Nutzer mit fremder Wohnanlagen-/WG-ID | Keine Einsicht und keine Änderung fremder Daten |
| Ungültige Eingabe oder unerlaubter Zustandswechsel | Vereinbarter Fehler; keine teilweise gespeicherte Änderung |
| Schreibender Aufruf in einer Entwicklungssitzung | Gültiges CSRF-Token erforderlich |
| Listen oder Detailansichten | Pagination, leere Ergebnisse und fehlende Datensätze funktionieren |

Für sitzungsbasierte MockMvc-Tests das Profil `dev` aktivieren und die echte Mock-Anmeldung mit Sitzung verwenden, wenn der Anmeldeweg Teil des Tests ist. Ein mit `csrf()` erzeugtes Testtoken ersetzt keinen gesonderten Test des tatsächlichen CSRF-Ablaufs. Fachtests benötigen eigene, reproduzierbare Testdaten; sich nicht auf feste Datenbank-IDs oder die Ausführungsreihenfolge anderer Tests verlassen.

Frontend-Tests mit `npm test` ausführen. Vitest ist eingerichtet; `src/i18n.test.ts` zeigt Tests für Sprachwahl und Ressourcen, `src/App.test.tsx` prüft Interaktionen mit React Testing Library und jsdom. Feature-Tests für relevante Zustände ergänzen. Zusätzlich Lint, Build und die betroffenen Abläufe im Browser mit den Mock-Rollen prüfen. Ergebnisse für die vereinbarten Geräte und Browser im Pull Request festhalten.

Die plattformabhängigen Prüfkommandos stehen in der [README](README.md#prüfungen). Die aktuelle [CI](.github/workflows/ci.yml) führt Backend-Verifikation, Frontend-Lint, -Tests und -Build sowie Compose-Konfigurationsprüfung und Docker-Builds aus. Sie verwendet JDK 21 und derzeit Node.js 22; die lokale Empfehlung ist Node.js 24. Bei neuen Node-Anforderungen diese Abweichung berücksichtigen und eine nötige CI-Anpassung abstimmen. Dokumentationsänderungen allein lösen diese Build-Pipeline wegen ihrer Pfadfilter nicht aus.

## 9. Dokumentation und Pull Request

Die Feature-Dokumentation gehört nach dem Prozessmodell unter `docs/features/<feature-name>/`, beispielsweise `docs/features/kontrollplanung/README.md`. Den Ordner bei Bedarf anlegen. Dort fachliches Verhalten, Rollen und Datenzuständigkeit, API-Vertrag, relevante Konfiguration und Prüfung beschreiben. Dokumentation im selben Pull Request wie das Feature aktualisieren.

Wesentliche Architekturentscheidungen gemäß [ADR-Workflow](docs/architektur/ADR-Workflow.md) dokumentieren. Neue ADRs beginnen als `Proposed`. Die Beschreibung einer geplanten Architektur ist kein Nachweis ihrer Umsetzung oder Freigabe.

Ein Pull Request enthält:

- Verweis auf Story und Akzeptanzkriterien sowie eine kurze Beschreibung des Problems und des neuen Verhaltens.
- Betroffene Fachmodule und Schnittstellen, insbesondere Änderungen für andere Teams.
- Berechtigungen und Datenzuständigkeit sowie relevante Konfigurations- oder Datenänderungen.
- Ausgeführte Prüfungen mit Ergebnissen und gegebenenfalls Screenshots der Oberfläche.
- Verweise auf Feature-Dokumentation und gegebenenfalls ADRs; offene Punkte ausdrücklich benennen.

Vor der Integration die aktuelle Definition of Done abgleichen: Code Review, erfolgreiche Tests und Builds, Prüfung der relevanten Endgeräte und Datenschutzvorgaben, aktuelle Dokumentation sowie die vorgesehenen Integrations-, Testumgebungs- und Abnahmeschritte. Technische Verantwortung des Lead Developers und fachliche Abnahme durch den zuständigen PO gemäß Teamvereinbarung einbeziehen.

## Checkliste für den ersten Schnitt

- [ ] Story, Akzeptanzkriterien, Abhängigkeiten und Zugriffsmatrix sind geklärt.
- [ ] Branch und Ziel der Integration sind mit dem Team abgestimmt.
- [ ] Backend-Code liegt im zuständigen Fachpaket; API verwendet DTOs.
- [ ] Privileg und Zugriff auf konkrete Datensätze werden serverseitig geprüft.
- [ ] Startdaten und Tests berücksichtigen die neuen Berechtigungen.
- [ ] Frontend-Routen und Navigation sind registriert; geschützte Loader und Actions prüfen Sitzung und Privileg.
- [ ] Frontend verwendet `api()` und behandelt Laden, Fehler und leere Ergebnisse.
- [ ] Erlaubte und verbotene Aktionen sind mit den betroffenen Rollen geprüft.
- [ ] Neue UI-Texte sind in Deutsch und Englisch vorhanden; Sprachwechsel und Fehleranzeigen funktionieren.
- [ ] Backend-Verifikation sowie Frontend-Lint, -Tests und -Build sind erfolgreich.
- [ ] Betroffene Konfiguration, Feature-Dokumentation und README sind aktuell.
- [ ] Pull Request ist nachvollziehbar beschrieben und zur Prüfung bereit.
