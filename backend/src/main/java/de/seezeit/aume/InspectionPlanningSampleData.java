package de.seezeit.aume;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import de.seezeit.aume.planning.InspectionPeriodRepository;
import de.seezeit.aume.planning.InspectionPlanningService;
import de.seezeit.aume.planning.InspectionPlanningService.CounterRequest;
import de.seezeit.aume.planning.InspectionPlanningService.CreatePeriodRequest;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Beispiel für einen offenen Gegenvorschlag in Egg. Sonne bleibt zum Planen im Browser frei. */
@Component
@Profile("dev")
@Order(4)
public class InspectionPlanningSampleData implements CommandLineRunner {
    private final InspectionPlanningService planning;
    private final InspectionPeriodRepository periods;
    private final ResidenceRepository residences;
    private final UserRepository users;

    public InspectionPlanningSampleData(InspectionPlanningService planning, InspectionPeriodRepository periods,
            ResidenceRepository residences, UserRepository users) {
        this.planning = planning;
        this.periods = periods;
        this.residences = residences;
        this.users = users;
    }

    @Override
    public void run(String... args) {
        var egg = residences.findFirstByName("Egg").orElse(null);
        var caretaker = users.findByUserKey("caretaker3").orElse(null);
        if (egg == null || caretaker == null || periods.existsByResidenceId(egg.getId())) return;

        var today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        var period = planning.create("clerk", new CreatePeriodRequest(egg.getId(), today.plusDays(14), today.plusDays(16),
            LocalTime.of(9, 0), LocalTime.of(16, 0), caretaker.getId()));
        planning.counter("caretaker3", period.id(), new CounterRequest(today.plusDays(21), today.plusDays(23),
            LocalTime.of(10, 0), LocalTime.of(14, 0), "In der ersten Woche bin ich im Urlaub.", period.version()));
    }
}
