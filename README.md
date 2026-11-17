# AUME2026

AUME2026 ist die Anwendung für Seezeit. Studierende, Objektbetreuung und Sachbearbeitung bereiten damit WG-Kontrollen vor, führen sie durch und veröffentlichen die Ergebnisse. Im Repository liegen das React-Frontend, das Spring-Boot-Backend und alles zum Starten.

Fachliches, Architektur und Absprachen im Team stehen in der [Dokumentation](docs/README.md).

## Voraussetzungen

| Komponente | Version |
| --- | --- |
| Node.js | `^20.19.0` oder `>=22.12.0` |
| JDK | 21 |
| Maven | 3.10.0 (Wrapper `backend/mvnw`) |
| React | 19.3.1 |
| TypeScript | 6.0.3 |
| Vite | 8.3.8 |
| Spring Boot | 4.1.2 |

## Lokaler Start

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Backend:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Windows: `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev`

Im expliziten `dev`-Profil legt das Backend eine H2-Datenbank im Speicher, die Wohnanlage „Wohnanlage Sonne“ und drei Mock-Nutzer an:

| Nutzer | Rolle | Berechtigung |
| --- | --- | --- |
| `student` | `STUDENT` | `GREETING_READ` |
| `caretaker` | `CARETAKER` | `RESIDENCE_READ` |
| `clerk` | `CLERK` | `RESIDENCE_READ` |

Das Frontend unter http://localhost:5173 bietet drei Schaltflächen zur Anmeldung an. Die Auswahl erstellt eine serverseitige Sitzung. Neuladen erhält die Anmeldung; „Abmelden“ beendet die Sitzung. Keycloak wird in diesem Profil nicht benötigt. Der Vite-Dev-Server leitet `/api` an Port 8080 weiter.

Studierende sehen den Gruß von `GET /api/greeting`; Objektbetreuung und Sachbearbeitung sehen ihre zugewiesene Wohnanlage unter `GET /api/residences`. Listen sind paginiert (`?page=0&size=20`) und enthalten `content` und `page`. Die API-Beschreibung steht unter http://localhost:8080/swagger-ui.html.

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

Der Keycloak-Client muss öffentlich sein (ohne Client-Secret), Standard Flow und PKCE unterstützen sowie genaue Redirect-, Logout- und Web-Origin-Adressen für das Frontend erlauben. Ein Audience-Mapper muss `seezeit-api` in die Access-Token aufnehmen. Der Issuer samt Discovery- und JWKS-Endpunkten muss vom Backend erreichbar sein. Signatur, Issuer, Audience und Gültigkeitszeit werden geprüft.

Ein verifiziertes Token wird über `(identity_issuer, identity_subject)` einem bereits angelegten Anwendungsnutzer zugeordnet. Diese Zuordnung und die Rollen müssen durch einen vertrauenswürdigen Provisionierungsprozess gesetzt werden; unbekannte Identitäten erhalten keinen Zugriff. Token-Rollen werden nicht als Anwendungsberechtigungen übernommen. Ein Provisionierungswerkzeug und ein betriebener Keycloak sind nicht Teil dieses Templates. Die bestehende H2-/`create-drop`-Konfiguration bleibt für das Template erhalten; dauerhafte Speicherung und Migrationen müssen vor einem echten Betrieb eingerichtet werden. ADR-0006 bleibt ein Vorschlag.

Prüfungen:

```bash
cd backend
./mvnw verify
```

```bash
cd frontend
npm run lint
npm run build
```

## Infrastruktur

Der Ordner `infrastructure/` bündelt den späteren Betrieb des Gesamtsystems. Dort liegt kein Quellcode von Frontend oder Backend.

- `compose.yaml` startet Frontend und Backend über die Dockerfiles in `frontend/` und `backend/`. Weitere Dienste kommen später dazu.
- `.env.example` ist die Vorlage für lokale Umgebungsvariablen. Echte Werte kommen in eine `.env`, die nicht eingecheckt wird.
- Die Dockerfiles liegen in `frontend/` und `backend/`. Jedes Image wird aus seinem eigenen Projekt gebaut. Compose verweist darauf.

## Start mit Docker

Docker muss laufen. Aus dem Projektroot für die Entwicklung mit drei Mock-Nutzern bauen und starten:

```bash
docker compose -f infrastructure/compose.yaml -f infrastructure/compose.dev.yaml up --build
```

`--build` baut die Images neu, bevor die Container starten. Das Frontend ist danach unter http://localhost erreichbar, das Backend unter http://localhost:8080.

Stoppen mit Strg+C. Container aufräumen:

```bash
docker compose -f infrastructure/compose.yaml -f infrastructure/compose.dev.yaml down
```

Der Entwicklungs-Override aktiviert `dev` ausdrücklich. Für Keycloak wird nur `infrastructure/compose.yaml` verwendet; die oben genannten Variablen können mit `--env-file infrastructure/.env` geladen werden. Nginx leitet `/api` an den Backend-Container weiter.
