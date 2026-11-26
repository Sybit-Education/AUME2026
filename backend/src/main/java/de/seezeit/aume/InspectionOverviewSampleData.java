package de.seezeit.aume;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.InspectionRepository;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity.Status;
import de.seezeit.aume.planning.InspectionPeriodRepository;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hängt nur bereits abgeschlossene Erstkontrollen der Wohnanlage Sonne an einen vergangenen
 * Zeitraum, damit die Übersicht eine letzte abgeschlossene Runde zeigen kann. Status und sonstige
 * Testdaten anderer Features bleiben unverändert. Egg behält den Gegenvorschlag aus
 * {@link InspectionPlanningSampleData}.
 */
@Component
@Profile("dev")
@Order(5)
public class InspectionOverviewSampleData implements CommandLineRunner {
    private final ResidenceRepository residences;
    private final UserRepository users;
    private final InspectionPeriodRepository periods;
    private final InspectionRepository inspections;

    public InspectionOverviewSampleData(ResidenceRepository residences, UserRepository users,
            InspectionPeriodRepository periods, InspectionRepository inspections) {
        this.residences = residences;
        this.users = users;
        this.periods = periods;
        this.inspections = inspections;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var sonne = residences.findFirstByName("Wohnanlage Sonne").orElse(null);
        var clerk = users.findByUserKey("clerk").orElse(null);
        if (sonne == null || clerk == null || periods.existsByResidenceId(sonne.getId())) return;

        var today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        var period = new InspectionPeriodEntity(sonne, today.minusDays(35), today.minusDays(33), clerk);
        period.setStatus(Status.AGREED);
        period.setAnnouncedBy(clerk);
        period.setAnnouncedAt(Instant.now().minusSeconds(34L * 24 * 60 * 60));
        periods.save(period);

        inspections.findAll().stream()
            .filter(i -> i.getFlat().getResidence().getId().equals(sonne.getId()))
            .filter(i -> i.getPeriod() == null && i.getRound() == 0)
            .filter(i -> i.getStatus() == InspectionEntity.Status.FINALIZED
                || i.getStatus() == InspectionEntity.Status.PUBLISHED)
            .forEach(inspection -> inspection.setPeriod(period));
    }
}
