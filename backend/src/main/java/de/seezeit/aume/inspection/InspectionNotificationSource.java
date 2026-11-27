package de.seezeit.aume.inspection;

import de.seezeit.aume.inspection.InspectionEntity.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lesender Vertrag für die Erkennung angekündigter WG-Kontrollen. */
@Service
@Transactional(readOnly = true)
public class InspectionNotificationSource {
    private static final List<Status> OPEN = List.of(Status.PLANNED, Status.DRAFT);

    private final InspectionRepository inspections;

    public InspectionNotificationSource(InspectionRepository inspections) {
        this.inspections = inspections;
    }

    /** Stabile ID-Reihenfolge für den seitenweisen Scan. Der erste Abruf beginnt bei ID 0. */
    public List<Candidate> findCandidates(LocalDate today, long afterId, int limit) {
        return inspections.findAnnouncedNotificationCandidates(afterId, today, OPEN, PageRequest.ofSize(limit))
            .stream().map(InspectionNotificationSource::candidate).toList();
    }

    /** Prüft den aktuellen Stand mit derselben Kontrollsperre wie der Kontrollabschluss. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<InspectionEntity> lockCandidate(long inspectionId, LocalDate today) {
        return inspections.findLockedById(inspectionId).filter(inspection -> {
            var period = inspection.getPeriod();
            return OPEN.contains(inspection.getStatus()) && period != null && period.getAnnouncedAt() != null
                && !period.getEndsOn().isBefore(today)
                && period.getResidence().getId().equals(inspection.getFlat().getResidence().getId());
        });
    }

    private static Candidate candidate(InspectionEntity inspection) {
        var period = inspection.getPeriod();
        return new Candidate(inspection.getId(), inspection.getFlat().getId(), period.getId(),
            period.getStartsOn(), period.getEndsOn(), period.getWindowStart(), period.getWindowEnd(),
            period.getAnnouncedAt(), period.getRescheduledAt());
    }

    /** Liefert die Rohzeitpunkte der aktuell gespeicherten offiziellen Terminrevision. */
    public record Candidate(Long inspectionId, Long flatId, Long periodId,
            LocalDate startsOn, LocalDate endsOn, LocalTime windowStart, LocalTime windowEnd,
            Instant announcedAt, Instant rescheduledAt) {}
}
