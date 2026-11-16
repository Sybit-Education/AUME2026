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

Backend (Windows):

```bash
cd backend
.\mvnw.cmd spring-boot:run
```

## Infrastruktur

Der Ordner `infrastructure/` bündelt den späteren Betrieb des Gesamtsystems. Dort liegt kein Quellcode von Frontend oder Backend.

- `compose.yaml` startet Frontend und Backend über die Dockerfiles in `frontend/` und `backend/`. Weitere Dienste kommen später dazu.
- `.env.example` ist die Vorlage für lokale Umgebungsvariablen. Echte Werte kommen in eine `.env`, die nicht eingecheckt wird.
- Die Dockerfiles liegen in `frontend/` und `backend/`. Jedes Image wird aus seinem eigenen Projekt gebaut. Compose verweist darauf.

## Start mit Docker

Docker muss laufen. Aus dem Projektroot bauen und starten:

```bash
docker compose -f infrastructure/compose.yaml up --build
```

`--build` baut die Images neu, bevor die Container starten. Das Frontend ist danach unter http://localhost erreichbar, das Backend unter http://localhost:8080.

Stoppen mit Strg+C. Container aufräumen:

```bash
docker compose -f infrastructure/compose.yaml down
```
