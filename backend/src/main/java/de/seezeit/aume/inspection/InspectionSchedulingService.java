package de.seezeit.aume.inspection;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import de.seezeit.aume.inspection.InspectionEntity.Status;
import de.seezeit.aume.inspection.ScheduledInspectionRepository.PeriodFlatCount;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Schnittstelle des Moduls {@code inspection} für die Kontrollplanung (US-17). */
@Service
@Transactional(readOnly = true)
public class InspectionSchedulingService {
    private static final Set<Status> OPEN = Set.of(Status.PLANNED, Status.DRAFT, Status.FINALIZED);

    private final ScheduledInspectionRepository inspections;

    InspectionSchedulingService(ScheduledInspectionRepository inspections) {
        this.inspections = inspections;
    }

    /** WGs, deren Kontrolle geplant, in Arbeit oder noch nicht veröffentlicht ist. */
    public Set<Long> flatsWithOpenInspection(Collection<Long> flatIds) {
        return flatIds.isEmpty() ? Set.of() : inspections.findFlatIdsWithStatus(flatIds, OPEN);
    }

    @Transactional
    public void schedule(Collection<FlatEntity> flats, InspectionPeriodEntity period) {
        flats.forEach(flat -> inspections.save(new InspectionEntity(flat, period)));
    }

    /** Entfernt die Kontrollen eines Zeitraums, solange keine davon begonnen wurde. */
    @Transactional
    public boolean removeIfNotStarted(InspectionPeriodEntity period) {
        var scheduled = inspections.findByPeriodId(period.getId());
        if (scheduled.stream().anyMatch(inspection -> inspection.getStatus() != Status.PLANNED)) return false;
        inspections.deleteAll(scheduled);
        return true;
    }

    public Map<Long, Long> flatCountByPeriod(Collection<Long> periodIds) {
        if (periodIds.isEmpty()) return Map.of();
        return inspections.countByPeriod(periodIds).stream()
            .collect(Collectors.toMap(PeriodFlatCount::getPeriodId, PeriodFlatCount::getFlatCount));
    }
}
