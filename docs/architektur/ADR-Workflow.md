# Architekturentscheidungen festhalten

Der [ADR-Index](adr/README.md) enthält alle Entscheidungen. Die Vorlage liegt unter [adr/template.md](adr/template.md).

1. Vorlage mit der nächsten freien vierstelligen Nummer und einem beschreibenden Dateinamen kopieren. Nummern nicht wiederverwenden.
2. Eine wesentliche Entscheidung pro ADR beschreiben: Kontext, Entscheidung, Alternativen, Folgen, Prüfung und verlinkte Anforderungen. Neue ADRs beginnen mit `Proposed`.
3. ADR und Index im selben Pull Request ergänzen. Die verantwortliche Rolle und betroffene Reviewer benennen. Der Review folgt dem bestehenden Prozessmodell.
4. Teamübergreifende Architektur entscheidet das Integrationsteam nach Abstimmung mit den betroffenen Lead Developern. Über den technischen Ansatz eines Schnitts entscheidet dessen Lead Developer. Fachliche Regeln bestätigt der zuständige PO; technische Seezeit-Vorgaben klärt das Integrationsteam mit Seezeit-IT.
5. Erst nach tatsächlicher Entscheidung `Accepted` sowie entscheidende Person und Datum eintragen. Bei Ablehnung `Rejected` und Begründung festhalten. Umsetzung und Testergebnisse separat im zugehörigen Issue oder Pull Request nachweisen.
6. Akzeptierte Entscheidungstexte stabil halten. Wesentliche Änderungen durch ein neues ADR vorschlagen; nach Annahme das alte mit `Superseded` und gegenseitigen Links ablösen. Nicht mehr geltende Entscheidungen ohne Ersatz erhalten `Deprecated`.

Offene Vorschläge und Wiedervorlagegründe werden bei der Sprintplanung berücksichtigt. Ein neuer Entwurf setzt akzeptierte ADRs nicht zurück. Die vorhandenen ADRs 0001–0004 nennen als Annahmedatum den 29.10.2026, aber keine namentlich entscheidende Person; dieser Nachweis ist nachzutragen, ohne eine neue Freigabe zu unterstellen.

Die Ablage lautet `docs/architektur/adr`, entsprechend dem [Prozessmodell](../projekt/prozessmodell/Prozessmodell.md).
