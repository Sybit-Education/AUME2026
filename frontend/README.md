# Seezeit-Frontend

React-/TypeScript-Frontend für das AUME2026-Backend. Für die Entwicklung Node.js 24 LTS mit aktuellem Patchstand (mindestens 24.15.0) verwenden; weitere unterstützte Versionen stehen unter `engines` in `package.json`. Die vollständige Einrichtung von Java, Node.js, Anmeldung und Docker steht im [Projekt-README](../README.md).

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
npm test
npm run build
```

`npm run preview` zeigt nach `npm run build` das gebaute Frontend standardmäßig unter http://localhost:4173. Der API-Proxy wird aus der Vite-Konfiguration übernommen; das Backend muss weiterhin laufen. Für die lokale Entwicklung mit automatischem Neuladen `npm run dev` verwenden; im Docker-Setup übernimmt Nginx den API-Proxy.

## Lokalisierung

Deutsch und Englisch sind über die Sprachauswahl verfügbar. Die Auswahl bleibt nach dem Neuladen erhalten. Ohne gespeicherte Auswahl wird die erste unterstützte Browsersprache verwendet, andernfalls Deutsch. Regionale Sprachcodes werden auf `de` beziehungsweise `en` reduziert.

- Übersetzungen: `src/locales/de.json` und `src/locales/en.json`.
- Initialisierung, Speicherung und HTML-Sprache: `src/i18n.ts`.
- React-Komponenten: `useTranslation()` aus `react-i18next` verwenden.
- Fehler: `UiError` aus `src/errors.ts` mit Übersetzungsschlüssel statt fertigem Text verwenden; erst beim Rendern übersetzen.
- API: `api()` aus `src/auth.ts` sendet `Accept-Language`; das Backend lokalisiert damit den Gruß.

Neue Schlüssel in beiden Sprachdateien ergänzen. `src/i18next.d.ts` typisiert die Schlüssel anhand der deutschen Datei. `npm test` prüft zusätzlich die Übereinstimmung der Sprachdateien sowie das Laufzeitverhalten. Regeln und Beispiele stehen in [CONTRIBUTING.md](../CONTRIBUTING.md#texte-und-lokalisierung).
