package de.seezeit.aume.residence;

import java.util.List;

/** Wohnanlage, WGs und Zuständigkeitsart aus Sicht eines Mitglieds des Betreuungsteams. */
public record StaffResidenceView(Long id, String name, StaffKind kind, List<String> flats) {}
