package de.seezeit.aume;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.UserEntity;
import de.seezeit.aume.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entwicklungsdaten für die Übersicht der Objektbetreuung nach dem Mockup in #41: eine angekündigte
 * Erstkontrolle, eine Nachkontrolle und ein Vorschlag, der auf die Antwort von {@code caretaker} wartet,
 * in der Wohnanlage Sonne. Läuft nur, solange es keine Kontrollen gibt, damit Startdaten anderer Features
 * (InspectionSampleData, Order 2) Vorrang haben; die Tests der Übersicht legen die Daten deshalb selbst
 * über {@link #seed()} an.
 */
@Component
@Profile("dev")
@Order(3)
public class OverviewSampleData implements CommandLineRunner {
    private final EntityManager entities;
    private final ResidenceRepository residences;
    private final UserRepository users;

    public OverviewSampleData(EntityManager entities, ResidenceRepository residences, UserRepository users) {
        this.entities = entities;
        this.residences = residences;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (entities.createQuery("select count(i) from InspectionEntity i", Long.class).getSingleResult() > 0) return;
        seed();
    }

    @Transactional
    public void seed() {
        var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        var clerk = users.findByUserKey("clerk").orElseThrow();
        var caretaker = users.findByUserKey("caretaker").orElseThrow();
        var today = LocalDate.now();

        var past = period(residence, today.minusDays(20), today.minusDays(18), clerk, caretaker, true);
        var announced = period(residence, today.plusDays(1), today.plusDays(3), clerk, caretaker, true);
        // Wartet auf die Antwort von caretaker (Beantworten in der Übersicht).
        var proposed = period(residence, today.plusDays(8), today.plusDays(10), clerk, caretaker, false);

        entities.persist(new InspectionEntity(flat(residence, "4.12", 4), announced));
        var failed = new InspectionEntity(flat(residence, "3.08", 3), past);
        failed.setStatus(InspectionEntity.Status.FINALIZED);
        failed.setOverallResult(InspectionEntity.Result.FAILED);
        failed.setInspectedAt(Instant.now().minus(19, ChronoUnit.DAYS));
        entities.persist(failed);
        entities.persist(new InspectionEntity(failed, today.plusDays(5)));
        entities.persist(new InspectionEntity(flat(residence, "3.10", 3), proposed));
    }

    private InspectionPeriodEntity period(ResidenceEntity residence, LocalDate startsOn, LocalDate endsOn,
            UserEntity clerk, UserEntity caretaker, boolean announce) {
        var period = new InspectionPeriodEntity(residence, startsOn, endsOn, clerk);
        period.setCaretaker(caretaker);
        if (announce) {
            period.setStatus(InspectionPeriodEntity.Status.AGREED);
            period.setAnnouncedBy(clerk);
            period.setAnnouncedAt(Instant.now());
        }
        entities.persist(period);
        return period;
    }

    private FlatEntity flat(ResidenceEntity residence, String label, int capacity) {
        return entities.createQuery("select f from FlatEntity f where f.residence = :residence and f.label = :label",
                FlatEntity.class)
            .setParameter("residence", residence).setParameter("label", label)
            .getResultStream().findFirst()
            .orElseGet(() -> {
                var flat = new FlatEntity(residence, label, capacity);
                entities.persist(flat);
                return flat;
            });
    }
}
