# Datenmodell

Die JPA-Entities im Backend sind das Schema. Hibernate legt die Tabellen im Profil `dev` per `ddl-auto: create-drop` in einer H2-Datenbank an. Es gibt noch keine Migrationen und keine PostgreSQL-Instanz.

Jedes Modul schreibt nur in die eigenen Tabellen ([ADR-0003](adr/0003-structure-the-backend-as-a-modular-monolith.md)). Fremdschlüssel über Modulgrenzen sind die gemeinsamen Verträge. Dateien selbst liegen im privaten Dateispeicher, in der Datenbank steht nur `attachments` ([ADR-0005](adr/0005-use-postgresql-and-private-attachment-storage.md)).

`users.residence_id` stammt aus dem Template (genau eine Wohnanlage pro Nutzer). Fachlich ersetzen das `memberships` für Bewohnende und `staff_assignments` für Personal. Für die Sachbearbeitung gilt bereits `staff_assignments`, für die Objektbetreuung bei Kontrollen ebenfalls (Betreuung und Vertretung, US-03). `users.user_type` ist die Sicht und einer von `STUDENT`, `CARETAKER` oder `CLERK`. Die Tabelle bleibt eine.

## Überblick

```mermaid
erDiagram
    residences ||--o{ flats : enthaelt
    residences ||--o{ users : template
    residences ||--o{ staff_assignments : zustaendigkeit
    users ||--o{ staff_assignments : personal
    flats ||--o{ memberships : bewohnt
    users ||--o{ memberships : mitglied
    users ||--o{ attachments : laedt_hoch

    checklist_versions ||--o{ checklist_criteria : enthaelt
    checklist_criteria ||--o{ criterion_images : bebildert
    checklist_criteria ||--o{ checklist_criterion_translations : uebersetzt
    attachments ||--o| criterion_images : datei

    residences ||--o{ inspection_periods : plant
    flats ||--o{ inspections : wird_kontrolliert
    inspection_periods ||--o{ inspections : im_zeitraum
    inspections ||--o{ inspections : nachkontrolle
    checklist_versions ||--o{ inspections : fassung
    inspections ||--o{ inspection_findings : bewertet
    checklist_criteria ||--o{ inspection_findings : kriterium
    inspection_findings ||--o{ finding_photos : belegt
    attachments ||--o| finding_photos : datei

    inspections ||--o{ inspection_revisions : korrigiert
    inspections ||--o{ escalations : eskaliert
    inspections ||--o{ inspection_comments : rueckfrage
    memberships ||--o{ handover_photos : einzug
    attachments ||--o| handover_photos : datei

    flats ||--o{ wg_tasks : organisiert
    wg_tasks ||--o{ wg_task_assignments : zuteilt

    users ||--o| notification_preferences : einstellung
    users ||--o{ notifications : hinweis
```

## Identität und Wohnen

Paket `de.seezeit.aume.residence` und `de.seezeit.aume.user`.

```mermaid
erDiagram
    residences ||--o{ flats : enthaelt
    residences ||--o{ users : template
    users }o--o{ roles : hat
    roles }o--o{ privileges : buendelt
    residences ||--o{ staff_assignments : anlage
    users ||--o{ staff_assignments : personal
    flats ||--o{ memberships : wg
    users ||--o{ memberships : bewohnende
    flats ||--o{ invitations : ziel
    users ||--o{ invitations : erstellt
    users |o--o{ invitations : loest_ein

    residences {
        bigint id PK
        varchar name
    }
    flats {
        bigint id PK
        bigint residence_id FK
        varchar label
        int capacity
    }
    users {
        bigint id PK
        varchar user_key UK
        varchar name
        varchar identity_issuer
        varchar identity_subject
        varchar user_type
        bigint residence_id FK
    }
    roles {
        bigint id PK
        varchar name UK
    }
    privileges {
        bigint id PK
        varchar name UK
    }
    staff_assignments {
        bigint id PK
        bigint user_id FK
        bigint residence_id FK
        varchar kind
    }
    memberships {
        bigint id PK
        bigint flat_id FK
        bigint user_id FK
        date valid_from
        date valid_to
    }
    invitations {
        bigint id PK
        bigint flat_id FK
        varchar token_hash UK
        timestamptz expires_at
        bigint created_by FK
        timestamptz created_at
        bigint used_by FK
        timestamptz used_at
        timestamptz revoked_at
    }
```

`memberships.valid_to` leer heißt aktuell eingezogen; sonst ist es der erste Tag ohne Zugriff. `invitations` speichert nur den Hash des einmaligen Tokens. `staff_assignments` ist je Nutzer und Wohnanlage eindeutig; eine Person hat pro Anlage also höchstens eine Zuständigkeit. `kind` ist `CARETAKER` (Objektbetreuung), `DEPUTY` (Vertretung, ebenfalls eine Objektbetreuung) oder `CLERK` (Sachbearbeitung). Pro Anlage gibt es höchstens eine Betreuung und eine Vertretung; das sichert der Service, nicht die Datenbank. `users` ist je `(identity_issuer, identity_subject)` eindeutig.

## Anhänge

Paket `de.seezeit.aume.attachment`. `storage_key` verweist auf die Datei im privaten Speicher. Im Profil `dev` liegen die Bytes unter `devdata/uploads`. `POST /api/attachments` und `GET /api/attachments/{id}` sind nur dort registriert; gelesen wird nur die eigene Datei.

```mermaid
erDiagram
    users ||--o{ attachments : laedt_hoch
    attachments {
        bigint id PK
        varchar storage_key UK
        varchar content_type
        bigint size_bytes
        bigint uploaded_by FK
        timestamptz uploaded_at
    }
```

## Standards

Paket `de.seezeit.aume.standard`. Eine veröffentlichte `checklist_versions`-Zeile bleibt unverändert; geänderte Kriterien ergeben eine neue Fassung ([ADR-0007](adr/0007-version-standards-and-preserve-published-inspection-history.md)).

```mermaid
erDiagram
    users ||--o{ checklist_versions : legt_an
    checklist_versions ||--o{ checklist_criteria : enthaelt
    checklist_criteria ||--o{ criterion_images : bebildert
    checklist_criteria ||--o{ checklist_criterion_translations : uebersetzt
    attachments ||--o| criterion_images : datei
    users ||--o{ info_pages : pflegt

    checklist_versions {
        bigint id PK
        int version_no UK
        varchar status
        bigint created_by FK
        timestamptz created_at
        timestamptz published_at
    }
    checklist_criteria {
        bigint id PK
        bigint checklist_version_id FK
        varchar area
        varchar title
        varchar description
        int position
    }
    checklist_criterion_translations {
        bigint id PK
        bigint criterion_id FK
        varchar language
        varchar area
        varchar title
        varchar description
    }
    criterion_images {
        bigint id PK
        bigint criterion_id FK
        bigint attachment_id FK
        varchar kind
    }
    info_pages {
        bigint id PK
        varchar slug UK
        varchar title
        varchar body
        bigint updated_by FK
        timestamptz updated_at
    }
```

`checklist_versions.status`: `DRAFT`, `PUBLISHED`, `RETIRED`. `criterion_images.kind`: `GOOD`, `BAD`.

`checklist_criteria` enthält die deutsche Fassung. `checklist_criterion_translations` ergänzt je Kriterium und Sprache (`language`, aktuell `en`) Bereich, Titel und Erklärung; `(criterion_id, language)` ist eindeutig. Fehlt eine Übersetzung, liefert die API den deutschen Text.

## Kontrollplanung

Paket `de.seezeit.aume.planning`. Ein Zeitraum gilt für eine Wohnanlage; die Kontrolle je WG liegt bei `inspections`.

```mermaid
erDiagram
    residences ||--o{ inspection_periods : plant
    users ||--o{ inspection_periods : legt_an
    users |o--o{ inspection_periods : kuendigt_an
    users |o--o{ inspection_periods : stimmt_ab

    inspection_periods {
        bigint id PK
        bigint residence_id FK
        date starts_on
        date ends_on
        varchar note
        bigint created_by FK
        timestamptz created_at
        bigint announced_by FK
        timestamptz announced_at
        timestamptz rescheduled_at
        varchar status
        varchar kind
        bigint caretaker_id FK
        time window_start
        time window_end
        date counter_starts_on
        date counter_ends_on
        time counter_window_start
        time counter_window_end
        varchar counter_reason
        bigint version
    }
```

`inspection_periods.status`: `WAITING_FOR_CARETAKER`, `COUNTER_PROPOSED`, `AGREED`. `kind`: `REGULAR`. Angekündigt ist ein Zeitraum, sobald `announced_at` gesetzt ist. `version` ist das Optimistic-Lock-Feld.

`rescheduled_at` ist optional und erfasst den Zeitpunkt der letzten Terminänderung nach der Ankündigung. US-18 setzt ihn in derselben Transaktion wie die neuen Werte, und zwar auch dann, wenn sich nur das Uhrzeitfenster ändert. Sonst bekämen die WGs bei einer reinen Uhrzeitänderung keine Nachricht. Bis einschließlich der Erstankündigung bleibt das Feld leer. Ohne Änderung lehnt die API ab (`period_unchanged`). Der US-15-Lesepfad verwendet das Feld bereits.

`inspection_announcements` ist der Versandeintrag aus US-18: eine Zeile je Ankündigung (`kind = ANNOUNCEMENT`) und je Terminänderung (`RESCHEDULE`). `sent_at` ist gleich dem gesetzten `announced_at` bzw. `rescheduled_at`. Zeitraum, Anzahl der WGs und Text halten den Stand beim Versand fest. `channels` ist kommagetrennt, aktuell nur `IN_APP`. `attachment_id` ist optional und verweist auf eine Datei, die die Sachbearbeitung selbst hochgeladen hat.

```mermaid
erDiagram
    inspection_periods ||--o{ inspection_announcements : versand
    users ||--o{ inspection_announcements : sendet
    residences ||--o{ inspection_announcements : an
    attachments |o--o{ inspection_announcements : anhang

    inspection_announcements {
        bigint id PK
        bigint period_id FK
        varchar kind
        bigint sent_by FK
        timestamptz sent_at
        bigint residence_id FK
        int flat_count
        varchar channels
        varchar message
        bigint attachment_id FK "optional"
        varchar attachment_name
        date starts_on
        date ends_on
        time window_start
        time window_end
    }
```

Hibernate legt die Spalte im aktuellen H2-Schema mit `ddl-auto: create-drop` an. Für persistente Installationen braucht es eine abgestimmte Migration, die die nullable Spalte ergänzt und bestehende Zeiträume ohne Änderungsnachweis leer lässt.

## Kontrollen

Paket `de.seezeit.aume.inspection`. Eine Nachkontrolle ist selbst eine `inspections`-Zeile mit `round` 1 oder 2 und `parent_inspection_id`. Das Gesamtergebnis setzt die prüfende Person.

```mermaid
erDiagram
    flats ||--o{ inspections : wird_kontrolliert
    inspection_periods |o--o{ inspections : im_zeitraum
    inspections |o--o{ inspections : nachkontrolle
    checklist_versions |o--o{ inspections : fassung
    users |o--o{ inspections : prueft
    users |o--o{ inspections : vier_augen
    users |o--o{ inspections : veroeffentlicht
    inspections ||--o{ inspection_findings : bewertet
    checklist_criteria ||--o{ inspection_findings : kriterium
    inspection_findings |o--o{ inspection_findings : erneut_geprueft
    inspection_findings ||--o{ finding_photos : belegt
    attachments ||--o| finding_photos : datei

    inspections {
        bigint id PK
        bigint flat_id FK
        bigint period_id FK
        bigint parent_inspection_id FK
        int round
        bigint checklist_version_id FK
        bigint inspector_id FK
        bigint co_inspector_id FK
        varchar status
        varchar overall_result
        date due_on
        timestamptz inspected_at
        bigint published_by FK
        timestamptz published_at
        boolean handover
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }
    inspection_findings {
        bigint id PK
        bigint inspection_id FK
        bigint criterion_id FK
        varchar rating
        varchar severity
        varchar note
        boolean building_cause_suspected
        bigint previous_finding_id FK
    }
    finding_photos {
        bigint id PK
        bigint finding_id FK
        bigint attachment_id FK
    }
```

`inspections.status`: `PLANNED`, `DRAFT`, `FINALIZED`, `PUBLISHED`. „Kontrolle abschließen“ durch die Objektbetreuung setzt direkt `PUBLISHED` mit `published_by` = prüfende Person; die WG sieht das Ergebnis sofort (Teamentscheidung 24.11.2026). `FINALIZED` wird derzeit nicht gesetzt. `overall_result`: `PASSED`, `FAILED`. `inspection_findings` ist je Kontrolle und Kriterium eindeutig. `rating`: `OK`, `DEFICIENT`, `RESOLVED_ON_SITE`. `severity` nur bei `DEFICIENT`: `LIGHT`, `SEVERE`. `version` ist das Optimistic-Lock-Feld.

## Ergebnisse und Nachkontrollen

Paket `de.seezeit.aume.result`. Nachkontrollen stehen in `inspections`. Hier liegen Korrekturen, Eskalationen, Rückfragen und Einzugsfotos.

```mermaid
erDiagram
    inspections ||--o{ inspection_revisions : korrigiert
    users ||--o{ inspection_revisions : autor
    inspections ||--o{ escalations : eskaliert
    users ||--o{ escalations : autor
    inspections ||--o{ inspection_comments : rueckfrage
    users ||--o{ inspection_comments : autor
    memberships ||--o{ handover_photos : einzug
    attachments ||--o| handover_photos : datei

    inspection_revisions {
        bigint id PK
        bigint inspection_id FK
        int revision_no
        varchar reason
        varchar snapshot
        bigint created_by FK
        timestamptz created_at
    }
    escalations {
        bigint id PK
        bigint inspection_id FK
        varchar kind
        varchar note
        bigint created_by FK
        timestamptz created_at
    }
    inspection_comments {
        bigint id PK
        bigint inspection_id FK
        bigint author_id FK
        varchar body
        timestamptz created_at
    }
    handover_photos {
        bigint id PK
        bigint membership_id FK
        bigint attachment_id FK
        varchar area
    }
```

`inspection_revisions` ist je Kontrolle und `revision_no` eindeutig; `snapshot` ist das veröffentlichte Ergebnis als JSON. `escalations.kind`: `CLEANING_COMPANY`, `WARNING`. Der Übergabezustand aus der Kontrolle vor dem Auszug ist `inspections.handover`.

## WG-Organisation

Paket `de.seezeit.aume.wg`. Nur für Mitglieder der WG sichtbar, nicht für Personal und nicht in Auswertungen.

```mermaid
erDiagram
    flats ||--o{ wg_tasks : organisiert
    checklist_criteria |o--o{ wg_tasks : leitet_ab
    wg_tasks ||--o{ wg_task_assignments : zuteilt
    users ||--o{ wg_task_assignments : uebernimmt

    wg_tasks {
        bigint id PK
        bigint flat_id FK
        varchar title
        bigint criterion_id FK
        int position
        bigint assignee_id FK
        varchar rhythm
        date start_week
        timestamptz archived_at
    }
    wg_task_assignments {
        bigint id PK
        bigint task_id FK
        bigint user_id FK
        date week_start
        timestamptz completed_at
    }
```

`wg_task_assignments` ist je Aufgabe und Woche eindeutig.

## Benachrichtigungen

Paket `de.seezeit.aume.notification`. `inspection_notifications` enthält den gemeinsamen fachlichen Inhalt. `notifications` enthält die persönlichen Zustellungen mit individuellem Gelesen-Zeitpunkt und kann auch allgemeine Hinweise ohne Kontrollnachricht speichern. Die vorhandenen Präferenzen und die Outbox bleiben unabhängig davon. Der Worker aus [ADR-0008](adr/0008-run-background-work-through-a-transactional-outbox.md) ist ein Architekturvorschlag.

```mermaid
erDiagram
    users ||--o| notification_preferences : einstellung
    users ||--o{ notifications : hinweis
    inspections ||--o{ inspection_notifications : kontrollnachricht
    checklist_versions o|--o{ inspection_notifications : anhangsfassung
    inspection_notifications o|--o{ notifications : zustellung

    notification_preferences {
        bigint user_id PK
        boolean email_enabled
        boolean push_enabled
        boolean task_reminders
    }
    notifications {
        bigint id PK
        bigint user_id FK
        bigint inspection_notification_id FK "optional"
        varchar type
        varchar title
        varchar link
        timestamptz created_at
        timestamptz read_at
    }
    inspection_notifications {
        bigint id PK
        bigint inspection_id FK
        varchar kind
        timestamptz announcement_revision_at
        date starts_on
        date ends_on
        time window_start
        time window_end
        bigint checklist_version_id FK "optional"
        timestamptz created_at
    }
    outbox_jobs {
        bigint id PK
        varchar type
        varchar payload
        varchar idempotency_key UK
        varchar status
        int attempts
        timestamptz next_attempt_at
        timestamptz locked_until
        varchar last_error
        timestamptz created_at
    }
```

`outbox_jobs.status`: `PENDING`, `DONE`, `FAILED`.

- Eine gemeinsame Nachricht ist je Kontrolle, Ankündigungsrevision und Nachrichtenart eindeutig. Die Arten sind `ANNOUNCEMENT`, `REMINDER_7_DAYS` und `REMINDER_1_DAY`. Pro gemeinsamer Nachricht ist höchstens eine Zustellung je Person möglich.
- Zeitraum und Zeitfenster halten den Stand der Nachricht fest. Ausschließlich Ankündigungen können auf eine Checklistenfassung verweisen. Das PDF wird beim berechtigten Download aus dieser Fassung erzeugt. Referenzierte veröffentlichte Fassungen einschließlich ihrer Kriterien und Übersetzungen müssen für spätere Downloads unverändert erhalten bleiben. Fassungen mit Status `RETIRED` bleiben als Anhang verfügbar. Beide Erinnerungen führen gemäß der abgestimmten Abweichung von US-16 (#20) zur interaktiven Checkliste.
- Die Postfachabfragen für Liste, Einzeleintrag und Ungelesen-Zähler berücksichtigen dieselbe Empfänger-ID und die aktive Mitgliedschaft in der WG der Kontrolle. `validFrom` gilt inklusive, `validTo` exklusiv. Die konkrete Zugriffspolitik beim Wiedereinzug steht aus. Die Abfragen allein würden bei einer erneuten aktiven Mitgliedschaft in derselben WG alte Zustellungen wieder sichtbar machen.
- Die lesende Kontrollquelle liefert angekündigte geplante Kontrollen und Entwürfe mit noch nicht abgelaufenem Zeitraum. Für die aktuelle Revision gilt `announcedAt` oder ein neueres `rescheduledAt`. Der Scheduler verarbeitet ausschließlich die noch gespeicherte offizielle Revision. Zwischen zwei Läufen überschriebene Revisionen werden bewusst nicht rekonstruiert. Bereits gespeicherte Nachrichten behalten ihren Zeitraum. Der Umgang mit Absagen bleibt offen.

## Audit

Paket `de.seezeit.aume.audit`. Auswertungen (Quoten, Mängelmuster) haben keine eigenen Tabellen; sie werden aus `inspections` und `inspection_findings` gelesen.

```mermaid
erDiagram
    users |o--o{ audit_events : handelt

    audit_events {
        bigint id PK
        timestamptz occurred_at
        bigint actor_id FK
        varchar entity_type
        bigint entity_id
        varchar action
        varchar details
    }
```
