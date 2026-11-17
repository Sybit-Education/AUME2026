# AUME2026

AUME2026 ist die Anwendung für Seezeit. Studierende, Objektbetreuung und Sachbearbeitung bereiten damit WG-Kontrollen vor, führen sie durch und veröffentlichen die Ergebnisse. Im Repository liegen das React-Frontend, das Spring-Boot-Backend und alles zum Starten.

Fachliches, Architektur und Absprachen im Team stehen in der [Dokumentation](docs/README.md). Wie neue Features in das Grundgerüst integriert werden, erklärt die [Contributing-Anleitung](CONTRIBUTING.md).

## Voraussetzungen

| Werkzeug | Version / Verwendung |
| --- | --- |
| Git | Repository klonen und Änderungen verwalten |
| JDK | 21, inklusive `javac` |
| Node.js mit npm | Für die Entwicklung im Team: Node.js 24 LTS mit aktuellem Patchstand (mindestens 24.15.0) |
| Maven | Wird in Version 3.10.0 vom eingecheckten Wrapper geladen; keine separate Installation erforderlich |
| Browser | Aktueller Browser für das Frontend |
| Docker mit Compose v2 | Optional für den Containerstart |

Das Frontend verwendet React, TypeScript und Vite, das Backend Spring Boot. Die konkreten Abhängigkeiten stehen in `frontend/package.json`, `frontend/package-lock.json` und `backend/pom.xml`. Mit `npm ci` werden die im Lockfile festgelegten Versionen installiert.

Für den lokalen Start mit dem Spring-Profil `dev` werden weder Keycloak noch eine externe Datenbank benötigt. Beim ersten Einrichten ist Internetzugang für Maven- und npm-Abhängigkeiten erforderlich. Die Ports 8080 und 5173 müssen frei sein.

## Entwicklungsumgebung einrichten

Repository klonen und im Editor öffnen. Die folgenden Anleitungen gelten für Windows, macOS und Linux; die Startbefehle stehen anschließend unter „Lokaler Start“. Bei Installern und Archiven die passende Prozessorarchitektur wählen, zum Beispiel x64 oder ARM64.

### Windows (PowerShell)

1. [Git für Windows](https://git-scm.com/downloads/win) installieren.
2. [Eclipse Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21) installieren. Im Installer die Optionen für `PATH` und `JAVA_HOME` aktivieren. Die [Windows-Installationsanleitung](https://adoptium.net/installation/windows) beschreibt diese Optionen.
3. [Node.js 24](https://nodejs.org/download/release/latest-v24.x/) über den passenden Windows-Installer (`.msi`) installieren; npm wird mitinstalliert.
4. PowerShell und die IDE nach der Installation neu öffnen.

Versionen in PowerShell prüfen:

```powershell
git --version
java -version
javac -version
node --version
npm.cmd --version
$env:JAVA_HOME
```

`JAVA_HOME` muss auf das JDK-Verzeichnis zeigen, nicht auf dessen `bin`-Unterordner. Java und javac sollen Version 21, Node.js Version 24 anzeigen. Falls mehrere Java-Versionen installiert sind, `JAVA_HOME` und den passenden `bin`-Eintrag im Windows-Dialog „Umgebungsvariablen“ anpassen.

Die Windows-Beispiele verwenden `npm.cmd`, damit PowerShell keine `npm.ps1` ausführen muss. Eine Änderung der PowerShell-Ausführungsrichtlinie ist dafür nicht erforderlich. Für das Backend den Windows-Wrapper `mvnw.cmd` verwenden.

### macOS (zsh)

[Git](https://git-scm.com/downloads/mac) und [Homebrew](https://brew.sh/) einrichten, falls noch nicht vorhanden. Anschließend:

```bash
brew install openjdk@21 node@24
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$(brew --prefix node@24)/bin:$JAVA_HOME/bin:$PATH"
```

Die beiden `export`-Zeilen einmal in `~/.zshrc` übernehmen, damit neue zsh-Terminals dieselben Werkzeuge verwenden. Danach ein neues Terminal öffnen oder `source ~/.zshrc` ausführen. Die Befehle verwenden `brew --prefix` und funktionieren damit unabhängig vom Homebrew-Installationspfad.

Ohne Homebrew können [Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21) und [Node.js 24](https://nodejs.org/download/release/latest-v24.x/) über die jeweiligen macOS-Downloads installiert werden. Für ein systemweit registriertes JDK 21 lässt sich `JAVA_HOME` mit `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` setzen.

### Linux (Bash oder zsh)

Git und JDK 21 über die Paketverwaltung der Distribution installieren. Beispiel für Ubuntu 24.04:

```bash
sudo apt update
sudo apt install git openjdk-21-jdk
```

Für andere Distributionen das entsprechende JDK-21-Paket oder die [Temurin-Installationsanleitung für Linux](https://adoptium.net/installation/linux) verwenden.

Node.js 24 mit npm über die [offiziellen Downloads](https://nodejs.org/download/release/latest-v24.x/) oder einen Versionsmanager installieren. Bei Verwendung von [nvm](https://github.com/nvm-sh/nvm#installing-and-updating) zuerst dessen Installationsanleitung befolgen und das Terminal neu öffnen. Danach:

```bash
nvm install 24
nvm alias default 24
nvm use 24
```

`JAVA_HOME` auf das installierte JDK 21 setzen und dessen `bin`-Verzeichnis in `PATH` aufnehmen. Wenn `javac` bereits auf JDK 21 zeigt, lässt sich der Pfad unter Linux so bestimmen:

```bash
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
export PATH="$JAVA_HOME/bin:$PATH"
```

Diese Einstellungen für Bash in `~/.bashrc`, für zsh in `~/.zshrc` übernehmen. Bei mehreren JDKs zunächst JDK 21 über die Java-Auswahl der Distribution auswählen, beispielsweise `update-alternatives` unter Ubuntu.

### Installation und IDE prüfen

Unter macOS und Linux:

```bash
git --version
java -version
javac -version
node --version
npm --version
echo "$JAVA_HOME"
```

Java und javac müssen Version 21, Node.js Version 24 anzeigen. Eine Java-Laufzeit ohne Compiler reicht nicht aus. Als Projekt-JDK in der IDE ebenfalls JDK 21 wählen; Maven soll dieses JDK verwenden. Die VS-Code-Java-Erweiterung kann für ihren Sprachserver ein neueres, mitgeliefertes JDK verwenden. Das Projekt-JDK bleibt 21. Lokale IDE-Konfigurationen sind nicht Voraussetzung für die Terminalbefehle.

## Lokaler Start

Zwei Terminals öffnen. Beide starten im Repository-Root, also im Verzeichnis mit `backend/`, `frontend/` und dieser README.

### Terminal 1: Backend

Windows (PowerShell):

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

macOS / Linux:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Das Backend läuft unter http://localhost:8080. Bei einem Start direkt aus der IDE die Hauptklasse `de.seezeit.aume.Application`, JDK 21 und das Spring-Profil `dev` verwenden, zum Beispiel mit dem Programmargument `--spring.profiles.active=dev`. Ohne dieses Profil werden die Keycloak-Einstellungen benötigt.

### Terminal 2: Frontend

Windows (PowerShell):

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

macOS / Linux:

```bash
cd frontend
npm ci
npm run dev
```

Anschließend http://localhost:5173 öffnen. `npm ci` ist nach einem frischen Checkout oder Änderungen am Lockfile erforderlich; für spätere Starts genügt `npm run dev` beziehungsweise `npm.cmd run dev`. Beide Prozesse laufen im Vordergrund und werden jeweils mit Strg+C beendet.

### Mock-Anmeldung und Testdaten

Im expliziten `dev`-Profil legt das Backend eine H2-Datenbank im Speicher, die Wohnanlage „Wohnanlage Sonne“ und drei Mock-Nutzer an:

| Nutzer | Rolle | Berechtigung |
| --- | --- | --- |
| `student` | `STUDENT` | `GREETING_READ` |
| `caretaker` | `CARETAKER` | `RESIDENCE_READ` |
| `clerk` | `CLERK` | `RESIDENCE_READ` |

Das Frontend unter http://localhost:5173 bietet drei Schaltflächen zur Anmeldung an. Die Auswahl erstellt eine serverseitige Sitzung. Neuladen erhält die Anmeldung; „Abmelden“ beendet die Sitzung. Keycloak wird in diesem Profil nicht benötigt. Der Vite-Dev-Server leitet `/api` an Port 8080 weiter.

Nach der Anmeldung öffnet sich die Übersicht unter `/`. Studierende sehen dort den Gruß von `GET /api/greeting`. Objektbetreuung und Sachbearbeitung öffnen über die Navigation `/residences`; diese Seite lädt die zugewiesenen Wohnanlagen von `GET /api/residences`. Listen sind paginiert (`?page=0&size=20`) und enthalten `content` und `page`. Die API-Beschreibung steht unter http://localhost:8080/swagger-ui.html.

## Frontend-Seiten und Feature-Integration

React Router verwaltet `/login`, die geschützte Übersicht `/`, die Wohnanlagen-Seite `/residences`, `/forbidden` und eine Ansicht für unbekannte URLs. Anmeldung und Navigation berücksichtigen die aktuellen Privilegien. Beim direkten Aufruf einer geschützten Seite wird das Ziel einschließlich Query und Fragment über die Anmeldung hinweg erhalten. Der vorhandene Nginx-SPA-Fallback unterstützt direkte Links und Neuladen im Docker-Frontend.

Feature-Teams ergänzen eigene Routen unter `frontend/src/features/` und registrieren sie in `frontend/src/app/router.ts`. Das gemeinsame Layout enthält Navigation, Sprachwahl und Logout. Die [Routing-Anleitung](frontend/ROUTING.md) erklärt Anmeldung, geschützte Loader, Navigation, Tests und die Erweiterung um neue Fachseiten. Kontrollseiten sind noch nicht implementiert.

## Sprachen und Lokalisierung

Die Oberfläche unterstützt Deutsch und Englisch. Über die Sprachauswahl im Kopfbereich lässt sich die Sprache ohne erneute Anmeldung wechseln. Gespeicherte Auswahl (`localStorage`, Schlüssel `aume.language`) hat Vorrang vor der ersten unterstützten Browsersprache; ohne passende Sprache wird Deutsch verwendet. Regionale Angaben wie `en-US` werden auf `en` abgebildet. Bei gesperrtem Browserspeicher funktioniert die Auswahl für die aktuelle Sitzung weiter.

Das Frontend verwendet `i18next` und `react-i18next`. Texte liegen in `frontend/src/locales/de.json` und `frontend/src/locales/en.json`; `frontend/src/i18n.ts` initialisiert die Übersetzungen, speichert die Auswahl und setzt das HTML-Attribut `lang`. Fehlende englische Texte fallen auf Deutsch zurück. Die Übersetzungen werden mit der Anwendung gebündelt und benötigen keinen externen Übersetzungsdienst.

Der gemeinsame API-Helfer sendet die gewählte Sprache als `Accept-Language`. Der Gruß unter `/api/greeting` verwendet Spring-Message-Bundles (`backend/src/main/resources/messages.properties` für Deutsch, `messages_en.properties` für Englisch) und liefert `Content-Language` zurück. Ohne unterstützte Anforderung ist die Antwort deutsch, unabhängig von der Sprache des Servers. Beim Sprachwechsel wird der aktuelle Inhalt erneut geladen. Namen, Wohnanlagen und andere Fachdaten werden nicht automatisch übersetzt. Technische API-Fehlerantworten sind kein Übersetzungsvertrag; die Oberfläche zeigt eigene übersetzte Meldungen an.

Für Keycloak wird die gewählte Sprache beim Anmelden mitgegeben; die verfügbaren Sprachen und Anmeldetexte müssen im Keycloak-Realm ebenfalls eingerichtet werden. Hinweise für neue Features stehen in der [Contributing-Anleitung](CONTRIBUTING.md#texte-und-lokalisierung).

## Rollen, Berechtigungen und Anmeldung

Nutzer haben mehrere Rollen; Rollen bündeln Berechtigungen. Beide werden als Spring-Security-Authorities geladen (`ROLE_STUDENT` für Rollen, zum Beispiel `GREETING_READ` für Berechtigungen). Die Startdaten sind wiederholbar, vorhandene Rollenzuweisungen werden nicht überschrieben. Rollen und Berechtigungen werden ausschließlich serverseitig zugewiesen. Es gibt keine öffentliche Nutzerregistrierung oder Rollenverwaltung.

`GET /api/me` liefert das angemeldete Nutzerprofil mit `roles` und `privileges`. Die bisherigen Nutzer- und Repository-Suchendpunkte sind entfernt. Wohnanlagen werden auf die Zuordnung des angemeldeten Nutzers begrenzt. WG-Mitgliedschaft und zeitliche Zugriffsregeln folgen mit den betreffenden Domänenmodulen; die aktuelle Zuordnung umfasst eine Wohnanlage pro Nutzer.

Im `dev`-Profil ist `POST /api/auth/dev-login` mit `{"userKey":"student"}` (alternativ `caretaker` oder `clerk`) verfügbar. Vor jedem schreibenden Aufruf wird über `GET /api/auth/csrf` ein Token geladen und unter dem zurückgegebenen `headerName` mitgesendet. Cookies müssen erhalten bleiben. `POST /api/auth/logout` beendet die Sitzung. Der Header `X-Dev-User` authentifiziert nicht mehr. Änderungen an Berechtigungen werden für bestehende Entwicklungssitzungen nach erneuter Anmeldung wirksam.

Ohne `dev`-Profil sind Mock-Anmeldung und Mock-Daten deaktiviert. Das Backend akzeptiert dann ausschließlich Keycloak-Bearer-Token; das Frontend verwendet den Keycloak-Adapter mit Authorization Code und PKCE. `GET /api/auth/config` liefert die öffentlichen Anmeldeeinstellungen. Folgende Umgebungsvariablen sind erforderlich:

| Variable | Beispiel |
| --- | --- |
| `KEYCLOAK_URL` | `https://identity.example.org` |
| `KEYCLOAK_REALM` | `seezeit` |
| `KEYCLOAK_CLIENT_ID` | `seezeit-frontend` |
| `KEYCLOAK_ISSUER_URI` | `https://identity.example.org/realms/seezeit` |
| `KEYCLOAK_AUDIENCE` | `seezeit-api` |

Der Keycloak-Client muss öffentlich sein (ohne Client-Secret), Standard Flow und PKCE unterstützen sowie passende Redirect-, Logout- und Web-Origin-Adressen für das Frontend erlauben. Der Login-Callback verwendet `/login?returnTo=...`; beispielsweise kann eine auf den konkreten Origin begrenzte Redirect-URI `https://app.example.org/login*` freigegeben werden. Weitere Details stehen in der [Routing-Anleitung](frontend/ROUTING.md#anmeldung-und-rückkehr-zur-angeforderten-seite). Ein Audience-Mapper muss `seezeit-api` in die Access-Token aufnehmen. Der Issuer samt Discovery- und JWKS-Endpunkten muss vom Backend erreichbar sein. Signatur, Issuer, Audience und Gültigkeitszeit werden geprüft.

Ein verifiziertes Token wird über `(identity_issuer, identity_subject)` einem bereits angelegten Anwendungsnutzer zugeordnet. Diese Zuordnung und die Rollen müssen durch einen vertrauenswürdigen Provisionierungsprozess gesetzt werden; unbekannte Identitäten erhalten keinen Zugriff. Token-Rollen werden nicht als Anwendungsberechtigungen übernommen. Ein Provisionierungswerkzeug und ein betriebener Keycloak sind nicht Teil dieses Templates. Die bestehende H2-/`create-drop`-Konfiguration bleibt für das Template erhalten; dauerhafte Speicherung und Migrationen müssen vor einem echten Betrieb eingerichtet werden. ADR-0006 bleibt ein Vorschlag.

## Prüfungen

Die Prüfungen benötigen keine laufenden Entwicklungsserver. Frontend-Tests laufen mit Vitest; sie prüfen unter anderem Sprachauswahl, Speicherung, Fallbacks und das Umschalten der Oberfläche. Die Backend-Tests wählen ihre Profile selbst und benötigen keinen Keycloak. Im Repository-Root beginnen.

Windows (PowerShell):

```powershell
cd backend
.\mvnw.cmd verify
cd ../frontend
npm.cmd ci
npm.cmd run lint
npm.cmd test
npm.cmd run build
cd ..
```

macOS / Linux:

```bash
cd backend
./mvnw verify
cd ../frontend
npm ci
npm run lint
npm test
npm run build
cd ..
```

## Fehlerbehebung

| Problem | Prüfung / Lösung |
| --- | --- |
| `java`, `javac`, `node` oder `npm` nicht gefunden | Installation und `PATH` prüfen; Terminal und IDE nach Änderungen neu öffnen. |
| Falsche Java-Version | `java -version` und `javac -version` müssen 21 anzeigen. `JAVA_HOME` prüfen: PowerShell `$env:JAVA_HOME`, Bash/zsh `echo "$JAVA_HOME"`. Projekt- und Maven-JDK in der IDE ebenfalls auf 21 setzen. |
| PowerShell blockiert `npm.ps1` | Die dokumentierten `npm.cmd`-Befehle verwenden. |
| PowerShell übergibt das Spring-Profil nicht korrekt | Das Maven-Argument vollständig in Anführungszeichen setzen: `"-Dspring-boot.run.profiles=dev"`. |
| `Permission denied` bei `./mvnw` | Unter macOS/Linux im Verzeichnis `backend` einmal `chmod +x mvnw` ausführen. |
| `bad interpreter` oder `sh\r` bei `mvnw` | Die Datei `backend/mvnw` mit LF-Zeilenenden auschecken bzw. im Editor als LF speichern. Unter Windows `mvnw.cmd` verwenden. |
| Fehlende `KEYCLOAK_*`-Variablen beim lokalen Start | Backend mit dem Profil `dev` starten; in der IDE das Profil ebenfalls eintragen. |
| Port bereits belegt | Vorherigen Backend-/Vite-Prozess beenden. Vite kann auf einen anderen Port ausweichen; dann die URL aus dem Terminal verwenden. |
| Frontend erreicht das Backend nicht oder die Mock-Anmeldung fehlt | Backend auf Port 8080 starten. Im Browser http://localhost:8080/api/auth/config und http://localhost:5173/api/auth/config öffnen; beide müssen `{"mode":"dev"}` liefern. |
| Maven- oder npm-Downloads schlagen fehl | Internetzugang und gegebenenfalls die Proxy-Konfiguration der eigenen Entwicklungsumgebung prüfen. |

## Infrastruktur

Der Ordner `infrastructure/` bündelt den späteren Betrieb des Gesamtsystems. Dort liegt kein Quellcode von Frontend oder Backend.

- `compose.yaml` startet Frontend und Backend über die Dockerfiles in `frontend/` und `backend/`. Weitere Dienste kommen später dazu.
- `.env.example` ist die Vorlage für lokale Umgebungsvariablen. Echte Werte kommen in eine `.env`, die nicht eingecheckt wird.
- Die Dockerfiles liegen in `frontend/` und `backend/`. Jedes Image wird aus seinem eigenen Projekt gebaut. Compose verweist darauf.

## Start mit Docker

Docker Engine mit Compose v2 unter Linux beziehungsweise Docker Desktop unter Windows/macOS installieren und starten. Dieser Weg baut Java und Node im Container; lokale JDK-/Node-Installationen sind dafür nicht erforderlich.

Die folgenden Befehle funktionieren in PowerShell, Bash und zsh. Aus dem Repository-Root für die Entwicklung mit drei Mock-Nutzern bauen und starten:

```bash
docker compose -f infrastructure/compose.yaml -f infrastructure/compose.dev.yaml up --build
```

`--build` baut die Images neu, bevor die Container starten. Das Frontend ist danach unter http://localhost erreichbar, das Backend unter http://localhost:8080.

Stoppen mit Strg+C. Container aufräumen:

```bash
docker compose -f infrastructure/compose.yaml -f infrastructure/compose.dev.yaml down
```

Der Entwicklungs-Override aktiviert `dev` ausdrücklich. Für Keycloak wird nur `infrastructure/compose.yaml` verwendet; die oben genannten Variablen können mit `--env-file infrastructure/.env` geladen werden. Nginx leitet `/api` an den Backend-Container weiter.
