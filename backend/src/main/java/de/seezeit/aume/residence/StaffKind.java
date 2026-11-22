package de.seezeit.aume.residence;

import java.util.Set;

/** Art der Zuständigkeit einer Person für eine Wohnanlage. */
public enum StaffKind {
    /** Zuständige Objektbetreuung. */
    CARETAKER,
    /** Vertretung der Objektbetreuung, selbst eine Objektbetreuung. */
    DEPUTY,
    /** Zuständige Sachbearbeitung; bestimmt, welche Anlagen sie sieht und bearbeitet. */
    CLERK;

    /** Die Zuständigkeiten, die die Sachbearbeitung pro Anlage festlegt. */
    public static final Set<StaffKind> CARETAKING = Set.of(CARETAKER, DEPUTY);
}
