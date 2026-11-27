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

## Develop-VM

Die VM steht im Hochschulnetz, nicht im öffentlichen Internet. Ports 80 und 8080 bleiben auf das VPN beschränkt.

Docker Engine und Compose v2 installieren. Nach grünem Publish auf `develop`:

```bash
git clone https://github.com/Sybit-Education/AUME2026.git
cd AUME2026
git switch develop
docker compose -f infrastructure/compose.develop-vm.yaml up -d
```

[`infrastructure/compose.develop-vm.yaml`](../infrastructure/compose.develop-vm.yaml) startet Frontend, Backend und `nickfedor/watchtower:1.24.2`. Das Backend läuft mit `SPRING_PROFILES_ACTIVE=dev`. Die Sample-Daten kommen aus diesem Profil. Die H2-Datenbank liegt im Speicher und ist nach einem Neustart wieder der Seed des aktuellen Images.

Prüfung: `http://<vm>/` und `http://<vm>/api/auth/config`. Die Config-Antwort ist `{"mode":"dev"}`. Anmeldung als `student`, `caretaker` oder `clerk`.

Watchtower ersetzt nur Container mit dem Label `com.centurylinklabs.watchtower.enable=true`, etwa alle fünf Minuten nach einem neuen `develop`-Digest. Eine Änderung an der Compose-Datei braucht auf der VM `git pull` und dasselbe `up -d`. Sofort ziehen:

```bash
docker compose -f infrastructure/compose.develop-vm.yaml run --rm watchtower --run-once
```

Öffentliche Packages brauchen kein Login. Schlägt der Pull fehl, auf der VM `docker login ghcr.io` mit einem Token `read:packages` ausführen und `~/.docker/config.json` in Watchtower nach `/config.json` mounten. Das Token bleibt außerhalb von Git.

## Staging und Produktion

Diese VMs werden noch nicht aufgesetzt. Staging nutzt später den Tag `staging` und denselben Stack wie Produktion: Datenbank, Upload-Volume, Keycloak, kein `dev`-Profil und keine Sample-Daten.

Die Produktion pinnt den Release-Tag, zum Beispiel `v1.2.0`. Watchtower übernimmt einen neuen Digest dieses Tags nach dem wöchentlichen Rebuild. Der Wechsel auf `v1.3.0` ist eine Änderung der Compose-Datei auf der VM. Danach baut der Zeitplan nur noch `v1.3.0` neu.
