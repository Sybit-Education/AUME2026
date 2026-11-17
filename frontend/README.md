# Seezeit-Frontend

React-/TypeScript-Frontend für das AUME2026-Backend. Die vollständige Einrichtung von Java, Node.js, Anmeldung und Docker steht im [Projekt-README](../README.md).

Aus dem Repository-Root in einem eigenen Terminal starten. Unter macOS/Linux:

```bash
cd frontend
npm ci
npm run dev
```

Unter Windows (PowerShell):

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Das Backend muss parallel auf Port 8080 mit dem Profil `dev` laufen. Das Frontend ist unter http://localhost:5173 erreichbar und leitet `/api` an das Backend weiter. Für die Entwicklung stehen drei Mock-Nutzer zur Verfügung: Studierende, Objektbetreuung und Sachbearbeitung. Keycloak ist im `dev`-Profil nicht erforderlich.

Prüfungen im Verzeichnis `frontend` (unter PowerShell entsprechend `npm.cmd` verwenden):

```bash
npm run lint
npm run build
```

`npm run preview` zeigt nach `npm run build` das gebaute Frontend standardmäßig unter http://localhost:4173. Der API-Proxy wird aus der Vite-Konfiguration übernommen; das Backend muss weiterhin laufen. Für die lokale Entwicklung mit automatischem Neuladen `npm run dev` verwenden; im Docker-Setup übernimmt Nginx den API-Proxy.
