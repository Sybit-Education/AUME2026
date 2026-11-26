package de.seezeit.aume;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
        if (sonne == null || clerk == null) return;

        var zone = ZoneId.of("Europe/Berlin");
        var todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        var controls = inspections.findAll();
        var completedInitials = controls.stream()
            .filter(i -> i.getFlat().getResidence().getId().equals(sonne.getId()))
            .filter(i -> i.getRound() == 0 && i.getInspectedAt() != null && i.getInspectedAt().isBefore(todayStart))
            .filter(i -> i.getStatus() == InspectionEntity.Status.FINALIZED
                || i.getStatus() == InspectionEntity.Status.PUBLISHED)
            .toList();
        // Die alte S-01-Erstkontrolle hat eine Nachkontrolle. Das veröffentlichte US-38-Beispiel hat keine.
        var firstFlat = completedInitials.stream()
            .filter(i -> i.getFlat().getLabel().equals("S-01"))
            .filter(i -> controls.stream().anyMatch(other -> other.getParentInspection() != null
                && other.getParentInspection().getId().equals(i.getId())))
            .toList();
        var secondFlat = completedInitials.stream()
            .filter(i -> i.getFlat().getLabel().equals("S-02"))
            .toList();
        if (firstFlat.size() != 1 || secondFlat.size() != 1) return;

        var historical = List.of(firstFlat.getFirst(), secondFlat.getFirst());
        // Beim Wiederholen sind beide bereits zugeordnet. Auch fremde Zuordnungen bleiben erhalten.
        if (historical.stream().anyMatch(i -> i.getPeriod() != null)) return;

        var dates = historical.stream().map(i -> i.getInspectedAt().atZone(zone).toLocalDate()).sorted().toList();
        var period = new InspectionPeriodEntity(sonne, dates.getFirst(), dates.getLast(), clerk);
        period.setStatus(Status.AGREED);
        period.setAnnouncedBy(clerk);
        period.setAnnouncedAt(dates.getFirst().minusDays(1).atStartOfDay(zone).toInstant());
        periods.save(period);

        historical.forEach(inspection -> inspection.setPeriod(period));
    }
}
