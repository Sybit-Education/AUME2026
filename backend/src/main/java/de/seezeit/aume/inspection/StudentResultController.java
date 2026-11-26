package de.seezeit.aume.inspection;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import de.seezeit.aume.inspection.FindingEntity.Rating;
import de.seezeit.aume.inspection.InspectionEntity.Result;
import de.seezeit.aume.inspection.InspectionResultService.ResidentResult;
import de.seezeit.aume.inspection.InspectionResultService.ResultCriterion;
import de.seezeit.aume.inspection.InspectionResultService.ResultFinding;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-38: Neuestes veröffentlichtes Kontrollergebnis der eigenen WG für Studierende.
 *
 * <p>Die WG ergibt sich aus der aktiven Mitgliedschaft (wie beim Kontrolltermin). Gezeigt werden nur
 * Ergebnisse ab dem Einzugstag. Die Daten kommen ausschließlich über {@link InspectionResultService}.
 */
@RestController
public class StudentResultController {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final StudentMembershipRepository memberships;
    private final InspectionResultService results;

    public StudentResultController(StudentMembershipRepository memberships, InspectionResultService results) {
        this.memberships = memberships;
        this.results = results;
    }

    @GetMapping("/api/student/inspection-results/latest")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public ResponseEntity<StudentResultView> latest(Principal principal) {
        var active = memberships.findActive(principal.getName(), LocalDate.now(ZONE));
        var flatIds = active.stream().map(m -> m.getFlat().getId()).distinct().toList();
        // Ohne aktive Mitgliedschaft (oder bei mehreren WGs gleichzeitig) gibt es keine eindeutige WG.
        if (flatIds.size() != 1) return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();

        var movedIn = active.getFirst().getValidFrom().atStartOfDay(ZONE).toInstant();
        var visible = results.resultsForFlat(flatIds.getFirst(), movedIn);
        if (visible.isEmpty()) return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(StudentResultView.of(visible.getFirst()));
    }

    public enum Kind { INITIAL, FOLLOW_UP }

    /** Ergebnis für Studierende. Enthält bewusst keine Angaben zur prüfenden Person. */
    public record StudentResultView(Long inspectionId, Kind kind, int round, Instant inspectedAt,
            Result overallResult, List<Deficiency> deficiencies, List<ResolvedOnSite> resolvedOnSite,
            List<PassedCriterion> passed) {

        static StudentResultView of(ResidentResult result) {
            var byCriterion = result.findings().stream()
                .collect(Collectors.toMap(ResultFinding::criterionId, Function.identity(), (first, second) -> first));
            var deficiencies = new ArrayList<Deficiency>();
            var resolved = new ArrayList<ResolvedOnSite>();
            var passed = new ArrayList<PassedCriterion>();
            // Reihenfolge wie auf der Checkliste
            result.criteria().stream()
                .sorted(Comparator.comparingInt(ResultCriterion::position))
                .forEach(criterion -> {
                    var finding = byCriterion.get(criterion.id());
                    var rating = finding == null ? Rating.OK : finding.rating();
                    switch (rating) {
                        case DEFICIENT -> deficiencies.add(new Deficiency(criterion.area(), criterion.title(),
                            finding.note(), finding.buildingCauseSuspected()));
                        case RESOLVED_ON_SITE -> resolved.add(new ResolvedOnSite(criterion.area(), criterion.title(),
                            finding.note()));
                        case OK -> passed.add(new PassedCriterion(criterion.area(), criterion.title()));
                    }
                });
            var kind = result.round() == 0 ? Kind.INITIAL : Kind.FOLLOW_UP;
            return new StudentResultView(result.inspectionId(), kind, result.round(), result.inspectedAt(),
                result.overallResult(), List.copyOf(deficiencies), List.copyOf(resolved), List.copyOf(passed));
        }
    }

    public record Deficiency(String area, String criterion, String note, boolean buildingCauseSuspected) {}

    public record ResolvedOnSite(String area, String criterion, String note) {}

    public record PassedCriterion(String area, String criterion) {}
}
