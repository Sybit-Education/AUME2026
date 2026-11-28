# Deployment

Frontend und Backend werden als Images veröffentlicht. Die Develop-VM zieht sie mit [Watchtower](https://github.com/nicholas-fedor/watchtower). Staging und Produktion warten auf den produktionsreifen Compose-Stack.

## Images

| Image | Registry |
| --- | --- |
| Frontend | `ghcr.io/sybit-education/seezeit-portal-frontend` |
| Backend | `ghcr.io/sybit-education/seezeit-portal-backend` |

[`.github/workflows/publish.yml`](../.github/workflows/publish.yml) baut beide Images, wenn ein Merge `backend/`, `frontend/`, `infrastructure/` oder den Workflow selbst ändert:

| Auslöser | Tags |
| --- | --- |
| Merge nach `develop` | `develop` |
| Merge nach `staging` | `staging` |
| GitHub Release `v*` (kein Pre-Release) | Versionstag, zum Beispiel `v1.2.0`, und `latest` |
| Pre-Release `v*` | nur der Versionstag |
| Push nach `main` | nichts |

`develop`, `staging` und `main` verlangen einen Merge Request mit grünem Check `CI`. Ein roter Test wird nicht veröffentlicht. `latest` ist nur der Alias des aktuellen Release. Die Produktion hängt nicht daran.

[`.github/workflows/rebuild-release.yml`](../.github/workflows/rebuild-release.yml) baut montags um 04:00 UTC das neueste veröffentlichte `v*`-Release erneut und schiebt denselben Versionstag plus `latest`. Der Git-Tag bleibt liegen. Ohne Release endet der Lauf erfolgreich. Der Zeitplan läuft erst, wenn die Datei auf `main` liegt. Manuell starten geht über *Run workflow*.

## VM einrichten

Jede VM bekommt dieselben Pakete. Docker und Compose v2 installieren und den Benutzer in die Gruppe `docker` aufnehmen:

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker "$USER"
```

Danach neu anmelden, damit die Gruppe gilt. `docker compose version` muss eine v2-Version ausgeben. Ohne dieses Plugin liest `docker` die Option `-f` selbst und bricht ab.

## Develop-VM

Die VM steht im Hochschulnetz, nicht im öffentlichen Internet. Ports 80 und 8080 bleiben auf das VPN beschränkt.

Nach grünem Publish auf `develop`:

```bash
git clone https://github.com/Sybit-Education/AUME2026.git
cd AUME2026
git switch develop
docker compose -f infrastructure/compose.develop.yaml up -d
```

Ohne `infrastructure/develop-vm.env` setzt Watchtower keine Notification-URL und ruft GitHub nicht auf. Image-Updates laufen trotzdem. Die Vorlage und das Benachrichtigungs-Template liegen schon im Repository.

Erst wenn das Dispatch-Token freigegeben ist, die Vorlage kopieren, `TOKEN` ersetzen und den Stack neu starten:

```bash
cp infrastructure/develop-vm.env.example infrastructure/develop-vm.env
docker compose -f infrastructure/compose.develop.yaml up -d
```

Das Token gilt nur für dieses Repository, Berechtigung Contents: Read and write. Die Datei bleibt auf der VM. Danach sendet Watchtower `repository_dispatch` mit `develop-deployed`, sobald ein beobachteter Container ein neues Image läuft. Ein abgelehnter Aufruf stoppt das Update nicht. Es gibt keinen Abruf der Seiten-URL. [`.github/workflows/watchtower.yml`](../.github/workflows/watchtower.yml) nimmt den Aufruf an und muss dafür auf `main` liegen. Der Job `confirm` in `publish.yml` ist auskommentiert, bis das Token freigegeben ist. Ein Lauf ohne Image-Wechsel sendet keinen Aufruf.

[`infrastructure/compose.develop.yaml`](../infrastructure/compose.develop.yaml) startet Frontend, Backend und `nickfedor/watchtower:1.24.2`. Das Backend läuft mit `SPRING_PROFILES_ACTIVE=dev`. Die Sample-Daten kommen aus diesem Profil. Die H2-Datei liegt auf der VM unter `devdata/db`. Der Backend-Container löscht sie bei jedem Start, damit ein neu gezogenes Image seine Sample-Daten neu anlegt. Der lokale Dev-Server mit `./mvnw` lässt die eigene Datei stehen. Keycloak und PostgreSQL laufen hier nicht. Die Anmeldung ist der Dev-Login.

Prüfung: `http://<vm>/` und `http://<vm>/api/auth/config`. Die Config-Antwort ist `{"mode":"dev"}`. Anmeldung über das Dropdown als `student`, `caretaker`, `caretaker2` oder `clerk`.

Watchtower ersetzt nur Container mit dem Label `com.centurylinklabs.watchtower.enable=true`, etwa alle fünf Minuten nach einem neuen `develop`-Digest. Eine Änderung an der Compose-Datei braucht auf der VM `git pull` und dasselbe `up -d`. Sofort ziehen:

```bash
docker compose -f infrastructure/compose.develop.yaml run --rm watchtower --run-once
```

Öffentliche Packages brauchen kein Login. Schlägt der Pull fehl, auf der VM `docker login ghcr.io` mit einem Token `read:packages` ausführen und `~/.docker/config.json` in Watchtower nach `/config.json` mounten. Das Token bleibt außerhalb von Git.

## Staging und Produktion

Diese VMs werden noch nicht aufgesetzt. Beide nutzen [`infrastructure/compose.prod.yaml`](../infrastructure/compose.prod.yaml):

```bash
SEEZEIT_TAG=staging docker compose -f infrastructure/compose.prod.yaml up -d
```

Der Stack ist PostgreSQL, ein privates Upload-Volume, Keycloak und Watchtower. Es gibt keine Sample-Daten und keinen Dev-Login. Angelegt werden die vier Testnutzer `student`, `clerk`, `caretaker` und `caretaker2`. Keycloak-Anmeldenamen sind `student`, `sachbearbeiter`, `objektbetreuer` und `objektbetreuer2`. Die Datenbank veröffentlicht keinen Port. Keycloak ist unter Port 8081 erreichbar, weil der Browser den Token-Endpunkt braucht. Die Admin-Oberfläche liegt in diesem Startmodus auf demselben Port.

Die Produktion setzt `SEEZEIT_TAG` auf den Release-Tag, zum Beispiel `v1.2.0`, und nicht auf `latest`. Watchtower übernimmt einen neuen Digest dieses Tags nach dem wöchentlichen Rebuild. Der Wechsel auf `v1.3.0` ist die Änderung von `SEEZEIT_TAG` auf der VM. Danach baut der Zeitplan nur noch `v1.3.0` neu.
