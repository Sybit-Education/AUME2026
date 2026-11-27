# Kontrolle durch die Objektbetreuung

Die Objektbetreuung arbeitet bei einer Kontrolle die veröffentlichte Checkliste einer WG ab, erfasst Mängel und legt das Gesamtergebnis fest. Aufbau und Texte folgen dem klickbaren Wireframe (Ansicht Objektbetreuung → Kontrolle).

## Ablauf

1. **Kontrollen → Aktuell** (`/inspections`) listet unter **Anstehende Kontrollen** (unter der Tabelle Anfragen) als Tabelle (Wohnanlage, WG, Erst- oder Nachkontrolle, Zeitraum mit Uhrzeit darunter, Status *Angekündigt*, *Geplant* oder *In Bearbeitung*) die Kontrollen der Wohnanlagen, die die angemeldete Person betreut oder vertritt (US-03), die jetzt durchgeführt werden können: begonnene (`DRAFT`) immer, geplante (`PLANNED`) nur, wenn ihr Zeitraum angekündigt ist oder sie keinen Zeitraum haben (z. B. Nachkontrollen). Sortiert nach Beginn des Zeitraums, Kontrollen ohne Zeitraum zuletzt. Noch nicht angekündigte Zeiträume stehen unter [Kontroll-Anfragen](../kontroll-anfragen/README.md). Angekündigte Kontrollen, deren Zeitraum noch nicht begonnen hat, stehen zum Planen schon in der Liste, statt „Kontrolle starten“ steht dort aber „ab TT.MM.“; starten lassen sie sich erst ab dem ersten Tag des Zeitraums.
2. **Kontrolle starten** in der Liste startet die Kontrolle direkt und öffnet die Checkliste (Adresse `/inspections/{id}?start=1`, danach ohne den Parameter, damit Neuladen nicht erneut startet; klappt der Start nicht, öffnet die Seite mit dem Hinweis). Der Start bindet die aktuell veröffentlichte Checkliste unveränderlich an die Kontrolle (ADR-0007) und setzt den Status von `PLANNED` auf `DRAFT`.
3. Je Bereich (Küche, Boden, Treppe, Bad) zeigt die Seite die Kriterien mit Beschreibung. Bewertet wird über:
   - **Bereich in Ordnung**: alle noch unbewerteten Kriterien des Bereichs auf `OK`
   - **Alles in Ordnung**: alle noch unbewerteten Kriterien der Kontrolle auf `OK`
   - **+ Mangel erfassen**: Kriterium, Schwere (Normal = `LIGHT`, Schwer = `SEVERE`) und Notiz (Pflicht) → `DEFICIENT`
   - Der erfasste Mangel steht direkt unter seinem Kriterium in der Checkliste, mit Schwere, Notiz und den Knöpfen **Vor Ort geklärt** (`RESOLVED_ON_SITE`), **Gebäudeursache** (`buildingCauseSuspected`) und **Löschen**
4. **Zum Abschluss** zeigt den Stand je Bereich. Das Gesamtergebnis (Bestanden = `PASSED`, Nachkontrolle erforderlich = `FAILED`) wählt die prüfende Person; vorgeschlagen wird `FAILED`, sobald ein offener Mangel existiert (US-34). Abschließen geht erst, wenn alle Kriterien bewertet sind. Es setzt den Status direkt auf `PUBLISHED` (veröffentlicht von der prüfenden Person, `published_by`/`published_at`): Die WG sieht das Ergebnis sofort, eine Freigabe durch die Sachbearbeitung gibt es nicht (Teamentscheidung 24.11.2026). `FINALIZED` wird nicht mehr gesetzt.

## Rollen und Daten

| Rolle | Berechtigung | Zugriff |
| --- | --- | --- |
| Objektbetreuung (`CARETAKER`) | `INSPECTION_PERFORM` | nur Kontrollen von WGs der Anlagen, die sie laut `staff_assignments` betreut (`CARETAKER`) oder vertritt (`DEPUTY`); fremde Kontrollen antworten mit 404 (US-03) |
| Studierende, Sachbearbeitung | – | 403 |

Daten: `inspections` und `inspection_findings` (Paket `inspection`), Kriterien aus `checklist_versions`/`checklist_criteria` über `ChecklistService` (Paket `standard`). Je Kontrolle und Kriterium gibt es höchstens einen Befund.

Gleichzeitige Änderungen: Jede ändernde Anfrage lädt die Kontrolle mit `InspectionRepository.findLockedById` (`SELECT … FOR UPDATE`). Anfragen an dieselbe Kontrolle laufen dadurch nacheinander; so entstehen keine doppelten Befunde, und eine Kontrolle kann nicht abgeschlossen werden, während gleichzeitig ein Befund gelöscht wird. Das `@Version`-Feld allein reicht dafür nicht, weil sich bei Befundänderungen die Kontrolle selbst nicht ändert.

## API

Alle Endpunkte verlangen `INSPECTION_PERFORM`. Änderungen sind nur im Status `DRAFT` erlaubt (sonst 409) und liefern die aktualisierte Kontrolle zurück.

| Methode und Pfad | Zweck |
| --- | --- |
| `GET /api/inspections?page=&size=` | durchführbare Kontrollen der betreuten und vertretenen Wohnanlagen wie oben, mit `residenceName` sowie `startsOn`, `endsOn`, `windowStart`, `windowEnd` des Zeitraums (leer ohne Zeitraum); paginiert, die Reihenfolge legt das Backend fest |
| `GET /api/inspections/{id}` | Kontrolle mit Kriterien und Befunden |
| `POST /api/inspections/{id}/start` | `PLANNED` → `DRAFT`, Checkliste binden; 409 ohne veröffentlichte Checkliste oder wenn sie keine Kriterien hat; 409 mit `{"code": "period_not_started"}`, solange der Zeitraum der Kontrolle noch nicht begonnen hat |
| `PUT /api/inspections/{id}/findings/{criterionId}` | Befund setzen: `{rating, severity, note, buildingCauseSuspected}`. Die Notiz wird getrimmt und darf danach höchstens 2000 Zeichen haben; `severity` und `buildingCauseSuspected` werden bei `OK` nicht gespeichert |
| `DELETE /api/inspections/{id}/findings/{criterionId}` | Befund entfernen |
| `POST /api/inspections/{id}/mark-ok` | `{area}` oder ohne Body: unbewertete Kriterien auf `OK` |
| `POST /api/inspections/{id}/finalize` | `{overallResult}`, `DRAFT` → `PUBLISHED`; 409, solange ein Kriterium unbewertet ist oder die Checkliste keine Kriterien hat |

Das Frontend zeigt bei 409 eine eigene Meldung und lädt die Kontrolle neu. Bei 401 leitet es zur Anmeldung weiter (Rückkehr zur Kontrolle), bei 403 auf `/forbidden`. „Kontrolle starten“ erscheint nur, wenn die Checkliste Kriterien hat. Auswahlfelder (Kriterium, Schwere, Gesamtergebnis) nutzen `components/Select.tsx` im App-Design statt `<select>`. Der Mangel-Dialog wählt ein Kriterium ohne bestehenden Mangel vor und weist darauf hin, wenn Speichern einen bestehenden Mangel ersetzt.

## Entwicklungsdaten

Im Profil `dev` legt `InspectionSampleData` eine veröffentlichte Checkliste (Fassung 1, zehn Kriterien aus dem Wireframe), für die WGs S-01 und S-02 der Wohnanlage Sonne (aus `SampleData`) je eine geplante Kontrolle an. Anmeldung als `caretaker`.

## Prüfung

- Backend: `InspectionTests` (Rechte, Ablauf bis zum Abschluss, fremde Wohnanlage, idempotente Startdaten, Notizlänge, Gebäudeursache nur bei Mängeln, Checkliste ohne Kriterien)
- Backend: `InspectionConcurrencyTests` (parallele Bewertungen, „Alles in Ordnung“ parallel zu Bewertungen, Abschließen parallel zum Löschen; ohne `@Transactional`, damit die Sperre wirkt)
- Frontend: `features/inspections/summary.test.ts` (Gruppierung nach Bereichen, Ergebnisvorschlag)

## Offen

- **Anwesende Bewohnende** (Feld im Wireframe-Abschluss) hat kein Feld im Datenmodell; mit dem Integrationsteam klären.
- **Fotos** zu Mängeln (`inspection_finding_photos`) und **Vergleichsbilder** je Kriterium (`criterion_images`) sind noch nicht angebunden.
- **Offline-Entwurf** bei Netzverlust (ADR-0011) fehlt.
- **Seitenleiste** aus dem Wireframe: die Navigation nutzt bis dahin die gemeinsame Kopfzeile.
- **Frontend-Tests** für Weiterleitung bei 401/403, Neuladen bei 409 und den Hinweis im Mangel-Dialog fehlen.
- Die Ansicht der Bewohnenden (Endpunkt, Recht, Seite) und Nachkontrollen bauen andere Teams; die Daten liefert die Schnittstelle unten.

## Schnittstelle für die Ansicht der Bewohnenden

Andere Module lesen veröffentlichte Ergebnisse einer WG über `InspectionResultService` (Paket `inspection`), nicht über eigene Abfragen auf `inspections` oder `inspection_findings` (ADR-0003).

| Methode | Liefert |
| --- | --- |
| `resultsForFlat(Long flatId)` | alle sichtbaren Ergebnisse der WG, neueste zuerst |
| `resultsForFlat(Long flatId, Instant notBefore)` | nur Ergebnisse, die ab `notBefore` veröffentlicht wurden, zum Beispiel ab dem Einzug |

`ResidentResult` enthält `inspectionId`, `flatId`, `flatLabel`, `round` (0 = Erstkontrolle, 1/2 = Nachkontrolle), `handover`, `overallResult`, `inspectedAt`, `publishedAt`, die Kriterien der gebundenen Checklisten-Fassung (`criteria`) und die Bewertungen (`findings` mit `rating`, `severity`, `note`, `buildingCauseSuspected`).

- **Sichtbar ab:** `InspectionResultService.VISIBLE_TO_RESIDENTS` (`PUBLISHED`). Entwürfe und geplante Kontrollen liefert der Service nie.
- **Datenschutz:** Die prüfende Person ist bewusst nicht enthalten. Notizen der Objektbetreuung sind enthalten, weil die WG sie lesen soll.
- **Keine Rechteprüfung:** Der Service kennt keine Bewohnenden. Der Aufrufer ermittelt über `memberships`, welche WG ein Nutzer sehen darf und ab wann, und prüft sein eigenes Privileg.

Beispiel für einen Endpunkt der Bewohnenden-Ansicht:

```java
var membership = /* aktuelle Mitgliedschaft des Nutzers aus memberships */;
var since = membership.getValidFrom().atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant();
return results.resultsForFlat(membership.getFlat().getId(), since);
```

Offen für die Bewohnenden-Ansicht: Übergabe-Kontrollen (`handover = true`) sind laut Datenmodell für neue Mietende gedacht und müssten dann auch vor dem Einzug sichtbar sein.

## Zuständigkeit (US-03)

Welche Anlagen eine Objektbetreuung sieht, liefert `StaffAccessService.residencesInCare(userId)` (Paket `residence`): alle Anlagen, für die sie in `staff_assignments` als Betreuung oder Vertretung eingetragen ist. Die Vertretung ist fest und hat bei Kontrollen dieselben Rechte wie die Betreuung; Terminvorschläge beantwortet nur die im Vorschlag eingetragene Betreuung (siehe [Kontroll-Anfragen](../kontroll-anfragen/README.md)). Das alte Feld `users.residence_id` gibt keinen Zugriff mehr. Es gilt für die Liste, das Öffnen und Bearbeiten einer Kontrolle, die Historie, die früheren Mängel und die Anlagenliste (`GET /api/residences`); die [Übersicht](../overview/README.md) arbeitet schon so. Ausnahme: Eine begonnene Kontrolle (`DRAFT`) darf die Person, die sie durchführt, auch nach einem Wechsel der Zuständigkeit noch öffnen, bearbeiten und abschließen.

Zum Ausprobieren: `caretaker2` (Login „Objektbetreuung (Vertretung)“) betreut Petershausen und vertritt Sonne und sieht die Kontrollen beider Anlagen, `caretaker` nur die von Sonne. Dafür legt `InspectionSampleData` in Petershausen eine sofort startbare Kontrolle für WG 3.09 an.

## Fotos zum Mangel (US-35)

Die Objektbetreuung kann einem Mangel Fotos zuordnen, damit der Zustand belegt ist (laut Story Map R2, mit PO und Lead vorgezogen).

- **Im Mangel-Dialog** gibt es wie im Wireframe das Feld **Foto (optional)** mit „Foto aufnehmen oder wählen“; auf dem Handy öffnet sich die Kamera. Gespeichert wird erst der Mangel, dann das Foto. Scheitert der Upload, bleibt der Mangel erhalten.
- **Beim Mangel** stehen die Vorschaubilder; ein Klick vergrößert. Im Entwurf gibt es zum Hinzufügen eine Plus-Kachel neben den Bildern und zum Entfernen einen kleinen Mülleimer oben rechts im Bild, höchstens fünf Fotos je Mangel.
- **Im Protokoll** einer abgeschlossenen Kontrolle stehen die Fotos nur lesend.

Ablauf: Die Oberfläche lädt die Datei über die Entwicklungsablage `POST /api/attachments` (#79, Integrationsteam) hoch und verknüpft den Anhang anschließend mit dem Mangel (`finding_photos`).

| Methode und Pfad | Zweck |
| --- | --- |
| `POST /api/inspections/{id}/findings/{criterionId}/photos` | `{attachmentId}` verknüpfen; nur eigene Uploads, nur Rasterbilder (JPEG, PNG, WebP, HEIC/HEIF; kein SVG, weil es Skripte enthalten kann), nur zu einem Mangel (nicht zu `OK`), höchstens fünf; nur im Status `DRAFT` |
| `DELETE /api/inspections/{id}/findings/{criterionId}/photos/{photoId}` | Verknüpfung entfernen; nur im Status `DRAFT` |
| `GET /api/inspections/{id}/photos/{photoId}` | Datei anzeigen; für alle, die für die Anlage zuständig sind (Betreuung und Vertretung), auch nach dem Abschluss; nur im Profil `dev`. Ausgeliefert mit `Content-Security-Policy: sandbox` und `nosniff`, andere Typen als Download |

`findings[].photoIds` in der Antwort einer Kontrolle listet die Fotos je Mangel. Wird ein Mangel gelöscht oder auf `OK` gesetzt, werden seine Foto-Verknüpfungen entfernt.

Fehler: 400 mit `{code}` `photo_needs_defect`, `not_an_image` oder `too_many_photos` (die Oberfläche zeigt je Code eine eigene Meldung); 404 für fremde Uploads und fremde Kontrollen; 409 `attachment_in_use` oder wenn die Kontrolle nicht mehr bearbeitbar ist.

Am Handy testen: Frontend mit `npm run build` und `npm run preview -- --host` starten und die angezeigte Netzwerkadresse (Port 4173) öffnen. Mit `npm run dev` lädt Vite die Seite neu, wenn der Browser beim Öffnen der Kamera in den Hintergrund geht; dann ist der offene Mangel-Dialog weg. Das betrifft nur den Entwicklungsserver. Geprüft auf einem echten Handy: Die Kamera öffnet sich, das Foto wird gespeichert.

Offen:

- Upload und Anzeige gibt es nur im Profil `dev`, solange die Ablage aus #79 nur dort existiert. Für den Betrieb braucht es die Ablage aus ADR-0005 (Integrationsteam).
- Entfernte Fotos bleiben als Datei und Zeile in `attachments` liegen; ein Aufräumen fehlt.
- Ob die WG die Fotos in ihrem Ergebnis sieht, ist nicht entschieden; `InspectionResultService` liefert keine Fotos.
- Das PDF-Protokoll enthält keine Fotos.

