# AUME2026

AUME2026 ist die Anwendung für Seezeit. Studierende, Objektbetreuung und Sachbearbeitung bereiten damit WG-Kontrollen vor, führen sie durch und veröffentlichen die Ergebnisse. Im Repository liegen das React-Frontend, das Spring-Boot-Backend und alles zum Starten.

Fachliches, Architektur und Absprachen im Team stehen in der [Dokumentation](docs/README.md). Das Datenmodell der Backend-Entities steht als ER-Diagramm im [Datenmodell](docs/architektur/datenmodell.md). Wie neue Features in das Grundgerüst integriert werden, erklärt die [Contributing-Anleitung](CONTRIBUTING.md). Dort stehen auch Seitenaufbau, Lokalisierung, Rollen und Anmeldung, die Prüfkommandos und der Ordner `infrastructure/`.

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

Für den lokalen Start mit dem Spring-Profil `dev` wird Keycloak unter Port 8081 benötigt. Eine externe Anwendungsdatenbank ist nicht erforderlich. Beim ersten Einrichten ist Internetzugang für Maven- und npm-Abhängigkeiten erforderlich. Die Ports 8080, 8081 und 5173 müssen frei sein.

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

Das Backend läuft unter http://localhost:8080. Spring Boot DevTools startet die Anwendung neu, sobald die IDE die Klassen neu baut. Maven selbst beobachtet die Quellen nicht. Bei einem Start direkt aus der IDE die Hauptklasse `de.seezeit.aume.Application`, JDK 21 und das Spring-Profil `dev` verwenden, zum Beispiel mit dem Programmargument `--spring.profiles.active=dev`. Keycloak muss unter http://localhost:8081 laufen, sonst kann das Backend die Realm-Daten beim Start nicht laden. Das Profil `dev` lädt die Fachdaten. Ohne dieses Profil gibt es keine verknüpften Nutzer.

Für Vorführungen gibt es zusätzlich das Profil `demo` (`"-Dspring-boot.run.profiles=dev,demo"`). Es legt für die Objektbetreuung `caretaker` in „Wohnanlage Sonne“ drei weitere WGs an: S-03 mit einer offenen Anfrage, S-04 angekündigt ab nächster Woche, S-05 angekündigt und bereits laufend. Die Tests nutzen nur `dev` und bleiben davon unberührt.

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

### Anmeldung und Testdaten

Im expliziten `dev`-Profil legt das Backend eine H2-Datenbank im Speicher, drei Wohnanlagen mit WGs und Zuständigkeiten sowie vier Mock-Nutzer an:

| Nutzer | Rolle | Berechtigung |
| --- | --- | --- |
| `student` | `STUDENT` | `GREETING_READ`, `INFO_PAGE_READ`, `CHECKLIST_READ`, `WG_MEMBERSHIP` |
| `caretaker` | `CARETAKER` | `RESIDENCE_READ`, `INSPECTION_PERIOD_RESPOND`, `INSPECTION_PERFORM`, `INSPECTION_OVERVIEW_READ` |
| `caretaker2` | `CARETAKER` | wie `caretaker` |
| `clerk` | `CLERK` | `RESIDENCE_READ`, `RESIDENCE_ASSIGN_STAFF`, `INSPECTION_PLAN`, `INFO_PAGE_READ`, `INFO_PAGE_EDIT`, `STANDARDS_VIEW`, `CLERK_INSPECTION_OVERVIEW_READ` |

`clerk` ist Sonne und Egg zugeordnet, nicht Petershausen. `caretaker` betreut Sonne; `caretaker2` betreut Petershausen und vertritt Sonne. Damit lässt sich prüfen, dass die Objektbetreuung nur Kontrollen ihrer betreuten und vertretenen Anlagen sieht (US-03). Weitere Nutzer in `SampleData` (`caretaker3`, `clerk2`) sind nur Stammdaten ohne Mock-Anmeldung.


Das Frontend unter http://localhost:5173 meldet im Profil `dev` die vier Mock-Nutzer ohne Keycloak an. Die Auswahl erstellt eine serverseitige Sitzung. Neuladen erhält die Anmeldung; „Abmelden“ beendet die Sitzung. Der Vite-Dev-Server leitet `/api` an Port 8080 weiter.

Außerhalb von `dev` erfolgt die Anmeldung mit Anmeldename und Passwort über Keycloak. `student` öffnet die Studierenden-Ansicht. `sachbearbeiter` ist der Anwendungsnutzer `clerk` und sieht die Sachbearbeitung. `objektbetreuer` ist der Anwendungsnutzer `caretaker` und sieht die Objektbetreuung.

Datei-Upload im Profil `dev`: `POST /api/attachments` mit dem Multipart-Feld `file` (höchstens 10 MB, derselbe CSRF-Schutz wie andere schreibende Aufrufe). Die Datei liegt unter `devdata/uploads` im Repository-Root, wenn das Backend aus `backend/` startet. Im Repository liegt nur `devdata/.gitkeep`. `GET /api/attachments/{id}` liefert die Datei nur an die Person zurück, die sie hochgeladen hat. Der gespeicherte Name ist ein zufälliger Schlüssel, nicht der ursprüngliche Dateiname.

## Fehlerbehebung

| Problem | Prüfung / Lösung |
| --- | --- |
| `java`, `javac`, `node` oder `npm` nicht gefunden | Installation und `PATH` prüfen; Terminal und IDE nach Änderungen neu öffnen. |
| Falsche Java-Version | `java -version` und `javac -version` müssen 21 anzeigen. `JAVA_HOME` prüfen: PowerShell `$env:JAVA_HOME`, Bash/zsh `echo "$JAVA_HOME"`. Projekt- und Maven-JDK in der IDE ebenfalls auf 21 setzen. |
| PowerShell blockiert `npm.ps1` | Die dokumentierten `npm.cmd`-Befehle verwenden. |
| PowerShell übergibt das Spring-Profil nicht korrekt | Das Maven-Argument vollständig in Anführungszeichen setzen: `"-Dspring-boot.run.profiles=dev"`. |
| `Permission denied` bei `./mvnw` | Unter macOS/Linux im Verzeichnis `backend` einmal `chmod +x mvnw` ausführen. |
| `bad interpreter` oder `sh\r` bei `mvnw` | Die Datei `backend/mvnw` mit LF-Zeilenenden auschecken bzw. im Editor als LF speichern. Unter Windows `mvnw.cmd` verwenden. |
| Backend startet nicht, weil Keycloak fehlt | Keycloak unter http://localhost:8081 starten. Die Standardwerte für `KEYCLOAK_*` stehen in `backend/src/main/resources/application.yml` und in `infrastructure/compose.yaml`. |
| Port bereits belegt | Vorherigen Backend-/Vite-Prozess beenden. Vite kann auf einen anderen Port ausweichen; dann die URL aus dem Terminal verwenden. |
| Frontend erreicht das Backend nicht oder die Anmeldung fehlt | Backend auf Port 8080 und Keycloak auf Port 8081 starten. Im Browser http://localhost:8080/api/auth/config und http://localhost:5173/api/auth/config öffnen; beide müssen `"mode":"keycloak"` liefern. |
| Maven- oder npm-Downloads schlagen fehl | Internetzugang und gegebenenfalls die Proxy-Konfiguration der eigenen Entwicklungsumgebung prüfen. |

## Start mit Docker

Docker Engine mit Compose v2 unter Linux beziehungsweise Docker Desktop unter Windows/macOS installieren und starten. Dieser Weg baut Java und Node im Container; lokale JDK-/Node-Installationen sind dafür nicht erforderlich.

Die folgenden Befehle funktionieren in PowerShell, Bash und zsh. Aus dem Repository-Root bauen und starten:

```bash
docker compose -f infrastructure/compose.yaml -f infrastructure/compose.dev.yaml up --build
```

`--build` baut die Images neu, bevor die Container starten. Das Frontend ist danach unter http://localhost erreichbar, das Backend unter http://localhost:8080. Die Keycloak-Admin-Oberfläche ist unter http://localhost:8081 erreichbar, Benutzer `admin`. Das Admin-Passwort steht lokal in `infrastructure/compose.yaml` und wird nicht in der README genannt. Beim Start legt Keycloak den Realm `seezeit` mit den Konten `student`, `sachbearbeiter` und `objektbetreuer` an. Die Anmeldung im Frontend verwendet diese Konten. `student` sieht die Studierenden-Ansicht, `sachbearbeiter` die Sachbearbeitung und `objektbetreuer` die Objektbetreuung. Das Profil `dev` lädt weiterhin die Fachdaten. Änderungen an der Realm-Datei gelten erst nach `down` und einem neuen `up`.

Stoppen mit Strg+C. Container aufräumen:

```bash
docker compose -f infrastructure/compose.yaml -f infrastructure/compose.dev.yaml down
```

Der Entwicklungs-Override aktiviert `dev` ausdrücklich und lädt damit die Fachdaten. Die Anmeldung ist auch in diesem Profil Keycloak. Nginx leitet `/api` an den Backend-Container weiter. Das Backend wartet, bis Keycloak gesund ist.
