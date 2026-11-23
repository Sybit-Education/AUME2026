# WG beitreten und verlassen (US-01, US X)

Studierende treten ihrer WG mit einem Einladungscode bei und können sie wieder verlassen. Die Seite liegt unter `/wg`.

## Regeln

- Nur Rolle `STUDENT` (Privileg `WG_MEMBERSHIP`). Personal hat keinen Zugriff auf diese Endpunkte.
- Die WG wird serverseitig aus der aktiven Mitgliedschaft bestimmt, nie aus einer ID der Oberfläche.
- Ein Code gilt einmal, ist befristet, widerrufbar und nur für die eigene Wohnanlage. Gespeichert wird nur sein SHA-256-Hash.
- Eine Person hat höchstens eine aktive Mitgliedschaft. Die Kapazität der WG wird beim Beitritt geprüft.
- `valid_to` ist der erste Tag ohne Zugriff. Der Austritt setzt es auf heute, der Zugriff endet also sofort. Für eine Rückkehr ist ein neuer Code nötig.

## API

| Methode und Pfad | Antwort |
| --- | --- |
| `GET /api/memberships/me` | `200` mit WG, Wohnanlage, Beitrittsdatum und Mitbewohnenden; `204` ohne WG |
| `POST /api/memberships` mit `{code}` | `201`; `400 invalid_code`, `409 already_member`, `409 flat_full` |
| `POST /api/memberships/me/leave` | `204`; `404 not_member` |

## Testdaten

Im Profil `dev` gibt es die Einmal-Codes `WG-SONNE-1` und `WG-SONNE-2` (WG S-01) sowie `WG-SONNE-3` (WG S-02). Ein Neustart des Backends setzt sie zurück.

## Offene Punkte

- Laut PO kommen QR-Codes und echte Einladungen frühestens im nächsten Sprint und werden vom System erzeugt, nicht von der Sachbearbeitung. Dafür muss `invitations.created_by` optional werden. Echte Codes müssen zufällig und lang genug sein.
- Fehlversuche beim Einlösen werden noch nicht begrenzt. Das ist vor dem Echtbetrieb nötig.
- Scan von QR-Codes und Anmeldung per E-Mail-Link sind nicht enthalten.
- Die Löschung der Putzplan-Daten nach dem Auszug fehlt, weil die Löschregel noch vom PO zu klären ist.
- Der Austritt durch Bewohnende selbst ist fachlich noch vom PO zu bestätigen.
