# Putzplan und Abhaken (US-06, US-07)

WG-Mitglieder planen unter `/student/cleaning-plan` gemeinsam ihre Putzaufgaben und haken Erledigtes ab.

## Regeln

- Nur Rolle `STUDENT` mit aktiver WG-Mitgliedschaft (Privileg `WG_MEMBERSHIP`). Sachbearbeitung und Objektbetreuung haben keinen Zugriff.
- Die WG wird serverseitig aus der aktiven Mitgliedschaft bestimmt. Aufgaben anderer WGs liefern `404`.
- Jedes Mitglied darf Aufgaben anlegen, ändern und löschen. Zugeteilt werden kann nur ein aktuelles Mitglied der eigenen WG.
- Die Aufgaben werden manuell verteilt, ohne automatische Rotation. Pro Aufgabe gibt es Person, Rhythmus und Startwoche.
- Rhythmus: wöchentlich, alle 2 Wochen oder alle 4 Wochen. „Monatlich“ ist damit vereinfacht abgebildet.
- Abhaken darf nur die zugeteilte Person in der aktuellen Woche, rückgängig machen nur, wer abgehakt hat. Alle Mitglieder sehen, wer was erledigt hat. Es gibt keine Punkte oder Rankings.
- Nach einem Auszug wird die Person im Plan nicht mehr genannt, ihre Aufgaben erscheinen als „Nicht zugeteilt“.
- Vorschläge sind die Kriterien der aktuell veröffentlichten Checkliste (US-21) in der gewählten Sprache. Sie erscheinen im Formular „Aufgabe anlegen“, nach Bereich gruppiert und beim Tippen gefiltert.
- Eine aus einem Vorschlag angelegte Aufgabe wird über `criterion_id` verknüpft. Der Vorschlag verschwindet dann auch nach Umbenennen oder Sprachwechsel und kommt erst beim Löschen der Aufgabe zurück. Ohne veröffentlichte Checkliste gibt es keine Vorschläge, eigene Aufgaben bleiben möglich.

## API

| Methode und Pfad | Antwort |
| --- | --- |
| `GET /api/wg/tasks?week=YYYY-MM-DD` | Plan der Woche mit `suggestions`; ohne `week` die aktuelle Woche; `404 not_member` ohne WG |
| `POST /api/wg/tasks` mit `{title, assigneeId, rhythm, startWeek, criterionId?}` | `201` |
| `PUT /api/wg/tasks/{id}` | `204` |
| `DELETE /api/wg/tasks/{id}` | `204`, die Aufgabe wird archiviert |
| `PUT /api/wg/tasks/{id}/completion` | `204`, hakt die aktuelle Woche ab |
| `DELETE /api/wg/tasks/{id}/completion` | `204`, macht das Abhaken rückgängig |

Fachliche Fehler kommen mit `{"code": …}`: `invalid_title`, `invalid_assignee`, `invalid_rhythm`, `invalid_criterion`, `not_assignee`, `not_due`.

## Offene Punkte

- Eine neue Checklisten-Version bekommt neue Kriterien-IDs. Bereits genutzte Vorschläge erscheinen dann wieder, bestehende Aufgaben bleiben unverändert.
- Die Löschung der Putzplan-Daten nach dem Auszug fehlt, bis die Löschregel feststeht.
- Rotation, Abwesenheit und Tausch aus dem Wireframe sind laut US-06 nicht Teil dieser Story.
