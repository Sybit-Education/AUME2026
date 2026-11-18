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

## Seiten und Routing

Die Anwendung verwendet React Router im Data Mode. `/login` bietet die Anmeldung, `/` die geschützte Übersicht und `/residences` die zugewiesenen Wohnanlagen für Nutzer mit `RESIDENCE_READ`. Unbekannte Seiten und fehlende Rechte haben eigene Ansichten. Direkte Links bleiben nach dem Anmelden erhalten; Neuladen stellt die Sitzung wieder her.

Neue Seiten als Feature-Routen unter `src/features/` ergänzen und in `src/app/router.ts` registrieren. Das gemeinsame Layout, `useAuth()`, geschützte Loader, Navigation und Tests sind in [ROUTING.md](ROUTING.md) mit Beispielen dokumentiert. Aufbau und Importregeln von `src/` erklärt die [Frontend-Ordnerstruktur](../docs/architektur/frontend-ordnerstruktur.md). Der Kontrollbereich ist eine künftige Erweiterung und noch nicht implementiert.

## Lokalisierung

Deutsch und Englisch sind über die Sprachauswahl verfügbar. Die Auswahl bleibt nach dem Neuladen erhalten. Ohne gespeicherte Auswahl wird die erste unterstützte Browsersprache verwendet, andernfalls Deutsch. Regionale Sprachcodes werden auf `de` beziehungsweise `en` reduziert.

- Übersetzungen: `src/locales/de.json` und `src/locales/en.json`.
- Initialisierung, Speicherung und HTML-Sprache: `src/lib/i18n.ts`.
- React-Komponenten: `useTranslation()` aus `react-i18next` verwenden.
- Fehler: `UiError` aus `src/lib/errors.ts` mit Übersetzungsschlüssel statt fertigem Text verwenden; erst beim Rendern übersetzen.
- API: `api()` aus `src/lib/auth/client.ts` sendet `Accept-Language`; das Backend lokalisiert damit den Gruß.

Neue Schlüssel in beiden Sprachdateien ergänzen. `src/lib/i18next.d.ts` typisiert die Schlüssel anhand der deutschen Datei. `npm test` prüft zusätzlich die Übereinstimmung der Sprachdateien sowie das Laufzeitverhalten. Regeln und Beispiele stehen in [CONTRIBUTING.md](../CONTRIBUTING.md#texte-und-lokalisierung).
