package de.seezeit.aume.user;

import org.springframework.data.jpa.repository.JpaRepository;

/** Nur Nutzer vom Typ Objektbetreuung; für die Auswahl von Betreuung und Vertretung. */
public interface CaretakerRepository extends JpaRepository<CaretakerEntity, Long> {
}
