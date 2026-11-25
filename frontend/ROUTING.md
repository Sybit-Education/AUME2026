# Seiten und Feature-Routen

Das Frontend verwendet React Router im Data Mode mit `createBrowserRouter`. Die bestehende Vite-Anwendung und die Spring-REST-API bleiben die Laufzeitbasis. Es wird kein zusätzlicher Frontend-Server benötigt.

## Vorhandene Seiten

| URL | Anmeldung / Berechtigung | Inhalt |
| --- | --- | --- |
| `/login` | Öffentlich | Drei Mock-Nutzer in `dev`, sonst Keycloak-Anmeldung |
| `/` | Angemeldet | Übersicht; Gruß mit `GREETING_READ`, Link zu Wohnanlagen mit `RESIDENCE_READ` |
| `/residences?page=0` | Angemeldet, `RESIDENCE_READ` | Zugewiesene Wohnanlagen mit Pagination |
| `/standards/info` | Angemeldet, `STANDARDS_VIEW` | Informationen für Bewohnende; bearbeiten mit `INFO_PAGE_EDIT`, sonst schreibgeschützt |
| `/residences?page=0&edit=<id>` | Zusätzlich `RESIDENCE_ASSIGN_STAFF` | Sachbearbeitung: Tabelle mit WGs und Zuständigkeit; `edit` öffnet das Bearbeiten, Speichern über die Route-Action |
| `/inspection-planning?residence=<id>` | Angemeldet, `INSPECTION_PLAN` | Kontrollzeitraum einer Wohnanlage planen und abstimmen |
| `/student` | Angemeldet | Leitet zur gemeinsamen Startseite `/` weiter |
| `/student/inspection` | Angemeldet, Rolle `STUDENT` | Gemeinsames Layout der Kontroll-Reiter, Index leitet zu `/student/inspection/appointment` weiter |
| `/student/inspection/appointment` | Angemeldet, Rolle `STUDENT` | Reiter „Termin“: Kontrolltermin der eigenen WG mit Kalenderexport |
| `/student/inspection/checklist` | Angemeldet, Rolle `STUDENT`, `CHECKLIST_READ` | Reiter „Checkliste“: Kriterien je Bereich mit Erklärungen (US-21, US-22) |
| `/student/inspection/process` | Angemeldet, Rolle `STUDENT`, `INFO_PAGE_READ` | Ablauf und Konsequenzen (US-23), Link unter der Checkliste; Reiter „Checkliste“ bleibt markiert |
| `/student/inspection/result` | Angemeldet, Rolle `STUDENT` | Reiter „Ergebnis“: Platzhalter für das eigene Kontrollergebnis |
| `/student/inspection/history` | Angemeldet, Rolle `STUDENT` | Reiter „Verlauf“: Platzhalter für den eigenen Kontrollverlauf |
| `/student/cleaning-plan` | Angemeldet | Platzhalter, bis das Putzplan-Feature die Route übernimmt |
| `/student/more` | Angemeldet | Weitere Studierenden-Funktionen, gruppiert |
| `/student/settings` | Angemeldet | Sprache wählen (US-26); unterer Tab „Mehr“ bleibt markiert |
| `/forbidden` | Angemeldet | Fehlende Berechtigung |
| Andere URLs | Öffentlich | Seite nicht gefunden |

### Präfix `student/`

Die Fachseiten der Studierenden-Ansicht verwenden das Präfix `/student/`, um Pfadkollisionen mit Personalrouten wie `/inspections` zu vermeiden. Sie hängen in `app/router.ts` unter `path: 'student'`. Die Features registrieren relative Pfade wie `checklist`, `appointment` und `more`. Die geschützte Startseite bleibt für alle Rollen unter `/` erreichbar. Wer ohne Sitzung `/student` öffnet, gelangt nach der Anmeldung über diese Indexroute zur Startseite.

Die früheren deutschen Adressen leiten im Browser weiter (`legacyStudentRedirects` in `app/router.ts`), damit Lesezeichen funktionieren:

| Alt | Neu |
|---|---|
| `/kontrolle`, `/kontrolle/termin` | `/student/inspection/appointment` |
| `/kontrolle/checkliste` | `/student/inspection/checklist` |
| `/kontrolle/ergebnis` | `/student/inspection/result` |
| `/kontrolle/verlauf` | `/student/inspection/history` |
| `/putzplan` | `/student/cleaning-plan` |
| `/mehr` | `/student/more` |
| `/mehr/einstellungen` | `/student/settings` |

Ein unterer Tab bleibt über `activeWithin` (Liste von Adressmustern in `app/navigation.ts`) auch auf Unterseiten markiert, z. B. „Kontrolle“ auf allen Reitern und „Mehr“ auf den Einstellungen.

Die Beispiele für `inspections` in dieser Anleitung beschreiben eine künftige Erweiterung.

## Zuständigkeiten im Code

```text
src/
  main.tsx                      Keycloak initialisieren, Browser-Router erstellen
  app/
    App.tsx                     AuthProvider und RouterProvider verbinden
    router.ts                   Seiten und Feature-Routen zusammenführen
    navigation.ts               Navigationspunkte zusammenführen
    AppLayout.tsx               Navigation, Sprache, Abmelden, Ladeanzeige und Outlet
    RequireAuth.tsx             Anmeldung für Kindseiten verlangen
    RouteErrorLayout.tsx        Gemeinsames Layout auch bei Ladefehlern
    routes/                     Fehler- und Hinweisseiten der Anwendung
  components/
    LoadingPage.tsx             Gemeinsame Ladeanzeige
  features/
    auth/                       Anmeldeseite und Login-Route
    dashboard/                  Übersichtsseite, Loader und API-Funktion
    residences/                 Fachseite, Routen, Navigationspunkt und API-Funktion
  lib/
    auth/
      client.ts                 API-/CSRF-/Keycloak-Helfer api(), login(), logout()
      AuthProvider.tsx          Gemeinsamen Sitzungszustand für React bereitstellen
      AuthContext.ts            useAuth() für Komponenten
      session.ts                Sitzungszustand und Laden von /api/me
      loaders.ts                protectedLoader für geschützte Datenabfragen
      RequirePrivilege.tsx      Seitenberechtigung prüfen
      response.ts               requireOk() für API-Antworten
      returnTo.ts               Sichere Rückkehr nach der Anmeldung
    errors.ts                   UiError und errorKey()
    i18n.ts                     Sprachinitialisierung
  locales/                      Sprachdateien
```

Jedes Feature besitzt seine Seiten, API-Funktionen und Routen. Gemeinsames Layout und Anmeldung werden zentral weiterentwickelt. Die konkrete Referenz ist [residences/routes.tsx](src/features/residences/routes.tsx). Begründung und Importregeln der Ordnerstruktur stehen in der [Contributing-Anleitung](../CONTRIBUTING.md#ordner-unter-frontendsrc).

## Ein neues Feature registrieren

Beispiel: Ein Team ergänzt eine Kontrollübersicht unter `/inspections`.

1. Backend-Endpunkt, DTO-Vertrag und Privileg `INSPECTION_READ` gemäß [CONTRIBUTING.md](../CONTRIBUTING.md) umsetzen.
2. Unter `src/features/inspections/` eine Seite, die API-Funktion und eine `routes.tsx` anlegen.
3. Das exportierte Routen-Array in `app/router.ts` importieren und **innerhalb der `RequireAuth`-Kindrouten** ergänzen.
4. Einen Navigationspunkt in `app/navigation.ts` aufnehmen, falls die Seite dort sichtbar sein soll.
5. Texte in beiden Sprachdateien ergänzen und direkte Seitenaufrufe sowie erlaubte und verbotene Zugriffe testen.

### API und Loader

Das folgende Beispiel setzt einen noch zu implementierenden paginierten Backend-Endpunkt sowie `InspectionPage` als Typ voraus. Zuerst `errors.inspections` in beiden Sprachdateien anlegen.

```typescript
// features/inspections/api.ts
import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import type { InspectionPage } from './types'

export async function listInspections(signal: AbortSignal): Promise<InspectionPage> {
  const response = requireOk(
    await api('/api/inspections?page=0&size=20', { signal }),
    'errors.inspections',
  )
  return (await response.json()) as InspectionPage
}
```

`api()` übernimmt Sitzung, CSRF, Bearer-Token und `Accept-Language`. `requireOk()` prüft die Antwort: `401` verwirft den lokalen Nutzerzustand, `403` führt zu `/forbidden`, weitere Fehler werden als übersetzbarer `UiError` weitergegeben. Der TypeScript-Typ ersetzt keine Laufzeitvalidierung der Serverantwort.

```tsx
// features/inspections/routes.tsx
import type { RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import InspectionListPage from './InspectionListPage'
import { listInspections } from './api'

export const inspectionRoutes: RouteObject[] = [{
  path: 'inspections',
  element: (
    <RequirePrivilege privilege="INSPECTION_READ">
      <InspectionListPage />
    </RequirePrivilege>
  ),
  loader: protectedLoader('INSPECTION_READ', ({ request }) =>
    listInspections(request.signal)),
}]
```

Die Seite liest ihre Daten mit `useLoaderData<InspectionPage | null>()`. Bei `null` vorübergehend die gemeinsame `LoadingPage` anzeigen, bis der Seitenguard die Weiterleitung abgeschlossen hat; so werden Übergänge nach einem Sitzungsablauf sicher behandelt. Für eine Detailseite kann ein weiteres Routenobjekt mit `path: 'inspections/:inspectionId'` ergänzt werden. Die ID aus `params` prüfen, bevor sie an die API geht. Für Listen Filter und Seitennummern über `useSearchParams()` verwalten; dadurch bleiben sie beim Teilen, Neuladen und Zurücknavigieren erhalten. Das Wohnanlagen-Feature zeigt diesen Ablauf mit `?page=...`.

`protectedLoader` ist erforderlich, weil React Router passende Parent- und Child-Loader parallel ausführen kann. Ein `RequireAuth`- oder `RequirePrivilege`-Element allein verhindert die Datenabfrage eines Child-Loaders nicht. Der Helfer wartet auf die gemeinsame, während der Sitzung zwischengespeicherte Sitzungsprüfung und führt die API-Funktion erst bei passender Berechtigung aus. Ohne Sitzung oder Privileg liefert er `null`; die zugehörigen Seitenguards übernehmen dann die Weiterleitung. Eine abgelaufene Sitzung wird ebenso behandelt. Deshalb geschützte Loader immer mit dem passenden Seitenguard kombinieren.

### Zentrale Registrierung und Navigation

In `app/router.ts` die vorhandene geschützte Gruppe erweitern:

```typescript
children: [
  ...dashboardRoutes,
  ...residenceRoutes,
  ...inspectionRoutes,
  // Weitere gemeinsame geschützte Seiten bleiben erhalten.
]
```

Für einen Navigationspunkt beispielsweise im Feature exportieren:

```typescript
export const inspectionNavigation = {
  to: '/inspections',
  label: 'navigation.inspections',
  privilege: 'INSPECTION_READ',
} as const
```

Diesen Eintrag in `app/navigation.ts` importieren und in das Array aufnehmen. `navigation.inspections` in `de.json` und `en.json` ergänzen. Die Schlüssel sind anhand der deutschen Sprachdatei typisiert. Das gemeinsame Layout filtert Menüpunkte anhand der Privilegien und markiert aktive Links. Navigation mit `Link`, `NavLink` oder `useNavigate()` aus `react-router` umsetzen. Routenpfade bleiben sprachunabhängig.

## Anmeldung und Rückkehr zur angeforderten Seite

`AuthProvider` und der gemeinsame Sitzungsdienst laden den Nutzer aus `/api/me`. In Komponenten liefert `useAuth()` den Nutzer, den Anmeldemodus und die Aktionen zum An- und Abmelden. Neue Features führen keine eigenen Login-Requests oder separate Nutzerzustände ein.

Wer eine geschützte URL ohne Sitzung öffnet, wird nach `/login?returnTo=...` weitergeleitet. Nach der Anmeldung wird der ursprüngliche interne Pfad einschließlich Query und Fragment wiederhergestellt. `safeReturnTo()` begrenzt das Ziel auf interne Anwendungsadressen und verhindert Rücksprünge auf die Login-Seite sowie externe Weiterleitungen.

Bei Keycloak führt der Callback auf `/login?returnTo=...`; der eigentliche Zielpfad bleibt als codierter Query-Wert erhalten. Der Callback selbst enthält kein Fragment. `main.tsx` initialisiert Keycloak, bevor der Browser-Router die URL erfasst. Diese Reihenfolge beibehalten, damit OAuth-Callback-Parameter verarbeitet werden können. Die Keycloak-Client-Konfiguration muss den Callback mit seinen Query-Parametern zulassen, zum Beispiel mit einer auf den konkreten Frontend-Origin begrenzten Redirect-URI `https://app.example.org/login*`. Die Post-Logout-Redirect-URI für den Frontend-Origin ebenfalls konfigurieren.

Abmelden verwirft nach erfolgreichem Server-Logout den lokalen Nutzerzustand. Geschützte Seiten gehen zurück zur Anmeldung. Ein API-`401` oder fehlgeschlagene Token-Erneuerung beendet den lokalen Anmeldestatus ebenfalls. Backend-Autorisierung und Zuständigkeitsprüfung bleiben für jeden Endpunkt verbindlich; Seiten- und Navigationsguards ergänzen sie.

## Ladezustände, Fehler und schreibende Aktionen

Das gemeinsame Layout zeigt Navigation und Sprachwahl. `useNavigation()` und `useRevalidator()` steuern die Ladeanzeige. Nicht behandelte Loader-Fehler erscheinen übersetzt im gemeinsamen Fehlerlayout; technische Fehlermeldungen werden nicht direkt ausgegeben. Die Schaltfläche zum erneuten Laden startet die Anwendung neu, auch wenn die ursprüngliche Authentifizierungsinitialisierung fehlgeschlagen ist.

Neue Schreibabläufe können React-Router-Actions verwenden. Actions müssen **selbst** Sitzung und Privileg prüfen, beispielsweise über `ensureSession()`; der Leseloader schützt eine Action nicht. Ohne Sitzung beziehungsweise Berechtigung vor dem API-Aufruf zur Anmeldung oder Fehlerseite weiterleiten. Requests über `api()` mit passender Methode und JSON-Header senden. Nach `requireOk()` Erfolg beziehungsweise fachliche Fehler behandeln; `SessionExpired` bei einer Action explizit in eine Login-Weiterleitung umsetzen. Serverseitig bleiben Rechte-, Zuständigkeits- und Eingabeprüfungen erforderlich. Ein vorhandenes Formular- und Action-Beispiel ist derzeit nicht Teil des Templates.

## Lokalisierung und Deployment

Beim Sprachwechsel revalidiert `AppLayout` die aktuellen Routen. Lokalisierte Loader-Daten werden dadurch neu geladen, ohne erneut anzumelden. Datenabfragen in eigenen Komponenten oder zusätzliche Caches müssen diesen Wechsel ebenfalls berücksichtigen. Neue Texte in beiden Sprachdateien pflegen.

Direkte Seitenaufrufe und Browser-Refresh funktionieren im Vite-Dev-Server und im Docker-Frontend: Die vorhandene Nginx-Konfiguration liefert für Seitenpfade `index.html`, während `/api/` an das Backend geht. Bei anderem Hosting denselben SPA-Fallback und die API-Weiterleitung einrichten. Frontend-Seitenpfade werden nicht im Spring-Backend registriert. Der gefundene beziehungsweise nicht gefundene Seiteninhalt wird clientseitig angezeigt; der statische Webserver liefert beim SPA-Fallback HTTP 200.

## Tests

`npm test` führt unter anderem die Routingtests in [App.test.tsx](src/app/App.test.tsx) aus. Sie erzeugen mit `createMemoryRouter(createAppRoutes(), ...)` dieselben Routen wie die Anwendung. Für neue Features diesen Aufbau verwenden und mindestens prüfen:

- Direkter Seitenaufruf mit vorhandener und fehlender Sitzung.
- Rückkehr zum angeforderten Ziel nach dem Login.
- Kein geschützter API-Aufruf ohne das nötige Privileg.
- Backend-`401` und `403` sowie fachliche Ladefehler.
- Query-Parameter, Zurücknavigation und Sprachwechsel, soweit relevant.

Zusätzlich direkte Links und Neuladen im Browser sowie beim verwendeten Hosting prüfen. Live-Keycloak-Anmeldung muss gegen den konfigurierten Realm getestet werden; die automatisierten Redirect-Tests ersetzen diesen Integrationstest nicht.

Grundlagen: [React Router Data Mode](https://reactrouter.com/start/data/installation), [Routenobjekte](https://reactrouter.com/start/data/route-object).
