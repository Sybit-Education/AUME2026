# AUME2026

Webanwendung mit React-Frontend und Spring-Boot-Backend.

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

## Start mit Docker

Platzhalter. Das Gesamtsystem (Frontend, Backend und weitere Dienste) wird später über Docker Compose gestartet. Der konkrete Ablauf folgt, sobald die Compose-Datei steht.

```bash
docker compose up
```
