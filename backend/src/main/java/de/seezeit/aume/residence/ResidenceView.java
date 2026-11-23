package de.seezeit.aume.residence;

import java.util.List;

/**
 * Wohnanlage mit ihren WG-Nummern und der zuständigen Objektbetreuung.
 * {@code deputy} ist {@code null}, wenn keine Vertretung eingetragen ist.
 */
public record ResidenceView(Long id, String name, List<String> flats, StaffView caretaker, StaffView deputy) {

    public record StaffView(Long id, String name) {}
}
