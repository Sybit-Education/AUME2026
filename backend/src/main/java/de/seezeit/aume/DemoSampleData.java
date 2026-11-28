package de.seezeit.aume;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import de.seezeit.aume.planning.InspectionPeriodRepository;
import de.seezeit.aume.planning.InspectionPlanningService;
import de.seezeit.aume.planning.InspectionPlanningService.CreatePeriodRequest;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.UserEntity;
import de.seezeit.aume.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zusätzliche Daten zum Vorführen der Objektbetreuung (#44, Reiter Anfragen und Anstehend). Nur mit dem Profil
 * {@code demo}, damit die Tests (Profil {@code dev}) unverändert bleiben. Start: {@code -Dspring-boot.run.profiles=dev,demo}.
 *
 * <p>Für die Objektbetreuung {@code caretaker} in „Sonnenbühl West I“ entstehen drei weitere WGs mit je einem Zeitraum:
 * <ul>
 *   <li>S-03: Vorschlag in zwei Wochen, wartet auf Antwort → Reiter <b>Anfragen</b></li>
 *   <li>S-04: abgestimmt und angekündigt, beginnt in einer Woche → <b>Anstehend</b> mit „ab TT.MM.“</li>
 *   <li>S-05: abgestimmt und angekündigt, läuft seit gestern → <b>Anstehend</b> mit „Kontrolle starten“</li>
 * </ul>
 */
@Component
@Profile("demo")
@Order(10)
public class DemoSampleData implements CommandLineRunner {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final InspectionPlanningService planning;
    private final InspectionPeriodRepository periods;
    private final ResidenceRepository residences;
    private final FlatRepository flats;
    private final UserRepository users;

    public DemoSampleData(InspectionPlanningService planning, InspectionPeriodRepository periods,
            ResidenceRepository residences, FlatRepository flats, UserRepository users) {
        this.planning = planning;
        this.periods = periods;
        this.residences = residences;
        this.flats = flats;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var sonne = residences.findFirstByName("Sonnenbühl West I").orElse(null);
        var caretaker = users.findByUserKey("caretaker").orElse(null);
        var clerk = users.findByUserKey("clerk").orElse(null);
        if (sonne == null || caretaker == null || clerk == null) return;
        if (flats.findByResidenceIdAndLabel(sonne.getId(), "S-03").isPresent()) return;

        var today = LocalDate.now(BERLIN);

        // Der Zeitraum nimmt jeweils alle freien WGs der Anlage, deshalb erst die WG, dann den Zeitraum.
        flats.save(new FlatEntity(sonne, "S-03", 3));
        propose(sonne, caretaker, today.plusDays(14), today.plusDays(16));

        flats.save(new FlatEntity(sonne, "S-04", 4));
        announce(propose(sonne, caretaker, today.plusDays(7), today.plusDays(9)), clerk, null);

        flats.save(new FlatEntity(sonne, "S-05", 2));
        // Ein Zeitraum in der Vergangenheit lässt sich nicht planen; er wird nach der Ankündigung vorverlegt.
        announce(propose(sonne, caretaker, today.plusDays(1), today.plusDays(3)), clerk, today.minusDays(1));
    }

    private Long propose(ResidenceEntity residence, UserEntity caretaker, LocalDate from, LocalDate to) {
        return planning.create("clerk", new CreatePeriodRequest(residence.getId(), from, to,
            LocalTime.of(9, 0), LocalTime.of(16, 0), caretaker.getId())).id();
    }

    /** Zustimmung der Objektbetreuung und Ankündigung; die Ankündigung hat noch keine eigene Funktion (US-18a). */
    private void announce(Long periodId, UserEntity clerk, LocalDate startsOn) {
        var period = periods.findById(periodId).orElseThrow();
        planning.agree("caretaker", periodId, period.getVersion());
        period = periods.findById(periodId).orElseThrow();
        if (startsOn != null) period.setStartsOn(startsOn);
        period.setAnnouncedAt(Instant.now());
        period.setAnnouncedBy(clerk);
        periods.saveAndFlush(period);
    }
}
