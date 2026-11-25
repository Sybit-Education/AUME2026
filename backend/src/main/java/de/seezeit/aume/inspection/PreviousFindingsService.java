package de.seezeit.aume.inspection;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.FindingEntity.Rating;
import de.seezeit.aume.inspection.FindingEntity.Severity;
import de.seezeit.aume.inspection.InspectionEntity.Result;

/**
 * Vorgeschichte einer WG (US-51): Mängel aus den letzten abgeschlossenen Kontrollen derselben WG,
 * damit die Objektbetreuung vor Ort gezielt auf frühere Schwachstellen schauen kann.
 */
@Service
@Transactional(readOnly = true)
public class PreviousFindingsService {
    /** So viele frühere Kontrollen reichen vor Ort; ältere stehen in der Kontroll-Historie. */
    static final int LIMIT = 3;

    private final InspectionRepository inspections;
    private final FindingRepository findings;
    private final AppUserService users;

    public PreviousFindingsService(InspectionRepository inspections, FindingRepository findings,
            AppUserService users) {
        this.inspections = inspections;
        this.findings = findings;
        this.users = users;
    }

    public List<PreviousInspection> previous(String userKey, Long id) {
        var user = users.requireUser(userKey);
        var current = inspections.findById(id)
            .filter(inspection -> inspection.getFlat().getResidence().getId().equals(user.getResidence().getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Für eine abgeschlossene Kontrolle zählt nur, was davor lag; vor dem Start alles bis jetzt.
        var before = current.getInspectedAt() != null ? current.getInspectedAt() : Instant.now();
        var previous = inspections.findPrevious(current.getFlat().getId(), InspectionHistoryService.DONE,
            current.getId(), before, PageRequest.of(0, LIMIT));
        if (previous.isEmpty()) return List.of();

        var defectsByInspection = findings.findByInspectionIdIn(previous.stream().map(InspectionEntity::getId).toList())
            .stream()
            .filter(finding -> finding.getRating() != Rating.OK)
            .collect(Collectors.groupingBy(finding -> finding.getInspection().getId()));

        return previous.stream().map(inspection -> new PreviousInspection(inspection.getId(),
            inspection.getInspectedAt(), inspection.getRound(), inspection.getOverallResult(),
            defectsByInspection.getOrDefault(inspection.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(finding -> finding.getCriterion().getPosition()))
                .map(finding -> new PreviousDefect(finding.getCriterion().getArea(), finding.getCriterion().getTitle(),
                    finding.getRating(), finding.getSeverity(), finding.getNote(), finding.isBuildingCauseSuspected()))
                .toList()))
            .toList();
    }

    /** {@code defects} enthält Mängel und vor Ort geklärte Befunde, keine OK-Bewertungen. */
    public record PreviousInspection(Long id, Instant inspectedAt, int round, Result overallResult,
        List<PreviousDefect> defects) {}

    /** Bereich und Kriterium als Text, damit die Zuordnung auch über Checklisten-Fassungen hinweg gelingt. */
    public record PreviousDefect(String area, String criterionTitle, Rating rating, Severity severity, String note,
        boolean buildingCauseSuspected) {}
}
