package de.seezeit.aume.residence;

/** Neue Zuständigkeit einer Anlage. {@code deputyId} darf fehlen, dann hat die Anlage keine Vertretung. */
public record StaffAssignmentRequest(Long caretakerId, Long deputyId) {}
