package de.seezeit.aume.inspection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.inspection.FindingEntity.Rating;
import de.seezeit.aume.inspection.FindingEntity.Severity;
import de.seezeit.aume.inspection.InspectionEntity.Result;
import de.seezeit.aume.inspection.InspectionEntity.Status;
import de.seezeit.aume.standard.ChecklistService;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.user.UserEntity;

/**
 * Durchführung einer Kontrolle durch die Objektbetreuung. Zugriff nur auf Kontrollen der eigenen
 * Wohnanlage; fremde Kontrollen verhalten sich wie nicht vorhanden (404).
 */
@Service
@Transactional
public class InspectionService {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final InspectionRepository inspections;
    private final FindingRepository findings;
    private final ChecklistService checklists;
    private final AppUserService users;

    public InspectionService(InspectionRepository inspections, FindingRepository findings,
            ChecklistService checklists, AppUserService users) {
        this.inspections = inspections;
        this.findings = findings;
        this.checklists = checklists;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Page<InspectionSummary> listOpen(String userKey, Pageable pageable) {
        var user = users.requireUser(userKey);
        // Die Reihenfolge legt die Abfrage fest, eine Sortierung aus der Anfrage wird nicht angewendet.
        var page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return inspections.findDue(user.getResidence().getId(), Status.PLANNED, Status.DRAFT, page)
            .map(inspection -> {
                var period = inspection.getPeriod();
                return new InspectionSummary(inspection.getId(), inspection.getFlat().getLabel(),
                    inspection.getFlat().getResidence().getName(), inspection.getStatus(), inspection.getRound(),
                    period == null ? null : period.getStartsOn(), period == null ? null : period.getEndsOn(),
                    period == null ? null : period.getWindowStart(), period == null ? null : period.getWindowEnd(),
                    period == null ? null : period.getKind());
            });
    }

    @Transactional(readOnly = true)
    public InspectionView view(String userKey, Long id) {
        return toView(require(users.requireUser(userKey), id));
    }

    /** PLANNED → DRAFT: bindet die aktuell veröffentlichte Checkliste unveränderlich an die Kontrolle. */
    public InspectionView start(String userKey, Long id) {
        var user = users.requireUser(userKey);
        var inspection = requireLocked(user, id);
        if (inspection.getStatus() != Status.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inspection was already started");
        }
        // Vor Beginn des angekündigten Zeitraums wird nicht kontrolliert. Ohne Zeitraum (z. B. Nachkontrolle) gilt keine Grenze.
        var period = inspection.getPeriod();
        if (period != null && period.getStartsOn().isAfter(LocalDate.now(BERLIN))) {
            throw new PeriodNotStartedException();
        }
        var version = checklists.currentPublished().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.CONFLICT, "No published checklist"));
        if (checklists.criteria(version).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Published checklist has no criteria");
        }
        inspection.setChecklistVersion(version);
        inspection.setInspector(user);
        inspection.setInspectedAt(Instant.now());
        inspection.setStatus(Status.DRAFT);
        return toView(inspection);
    }

    /** Legt die Bewertung eines Kriteriums an oder ersetzt sie. */
    public InspectionView rate(String userKey, Long id, Long criterionId, FindingRequest request) {
        var inspection = requireDraft(users.requireUser(userKey), id);
        var criterion = criteria(inspection).stream().filter(c -> c.getId().equals(criterionId)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criterion not in checklist"));
        if (request == null || request.rating() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating is required");
        }
        boolean deficient = request.rating() == Rating.DEFICIENT;
        if (deficient && (request.severity() == null || !StringUtils.hasText(request.note()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deficiencies need a severity and a note");
        }
        var note = StringUtils.hasText(request.note()) ? request.note().strip() : null;
        if (note != null && note.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note is too long");
        }
        var finding = findings.findByInspectionIdAndCriterionId(inspection.getId(), criterion.getId())
            .orElseGet(() -> new FindingEntity(inspection, criterion, request.rating()));
        finding.setRating(request.rating());
        finding.setSeverity(deficient ? request.severity() : null);
        finding.setNote(note);
        // Ein Verdacht auf bauliche Ursache gehört nur zu einem Mangel, nicht zu einer OK-Bewertung.
        finding.setBuildingCauseSuspected(request.rating() != Rating.OK
            && Boolean.TRUE.equals(request.buildingCauseSuspected()));
        findings.save(finding);
        return toView(inspection);
    }

    public InspectionView clear(String userKey, Long id, Long criterionId) {
        var inspection = requireDraft(users.requireUser(userKey), id);
        findings.findByInspectionIdAndCriterionId(inspection.getId(), criterionId).ifPresent(findings::delete);
        return toView(inspection);
    }

    /** Setzt alle noch unbewerteten Kriterien eines Bereichs, ohne Bereich alle, auf OK. */
    public InspectionView markOk(String userKey, Long id, String area) {
        var inspection = requireDraft(users.requireUser(userKey), id);
        var rated = findings.findByInspectionId(inspection.getId()).stream()
            .map(finding -> finding.getCriterion().getId()).toList();
        criteria(inspection).stream()
            .filter(criterion -> area == null || criterion.getArea().equals(area))
            .filter(criterion -> !rated.contains(criterion.getId()))
            .forEach(criterion -> findings.save(new FindingEntity(inspection, criterion, Rating.OK)));
        return toView(inspection);
    }

    /**
     * DRAFT → PUBLISHED. Das Gesamtergebnis legt die prüfende Person fest (US-34, ADR-0007). Abschließen
     * veröffentlicht zugleich: die WG sieht das Ergebnis sofort, eine Freigabe durch die Sachbearbeitung
     * gibt es nicht (Teamentscheidung 24.11.2026). {@code FINALIZED} wird dadurch nicht mehr gesetzt.
     */
    public InspectionView finalizeInspection(String userKey, Long id, Result overallResult) {
        var user = users.requireUser(userKey);
        var inspection = requireDraft(user, id);
        if (overallResult == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Overall result is required");
        }
        var criteria = criteria(inspection);
        if (criteria.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checklist has no criteria");
        }
        if (findings.findByInspectionId(inspection.getId()).size() < criteria.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Every criterion must be rated");
        }
        inspection.setOverallResult(overallResult);
        inspection.setStatus(Status.PUBLISHED);
        inspection.setPublishedBy(user);
        inspection.setPublishedAt(Instant.now());
        return toView(inspection);
    }

    private InspectionEntity require(UserEntity user, Long id) {
        return inspections.findById(id)
            .filter(inspection -> inspection.getFlat().getResidence().getId().equals(user.getResidence().getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Wie {@link #require}, sperrt die Kontrolle aber für die restliche Transaktion. */
    private InspectionEntity requireLocked(UserEntity user, Long id) {
        return inspections.findLockedById(id)
            .filter(inspection -> inspection.getFlat().getResidence().getId().equals(user.getResidence().getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private InspectionEntity requireDraft(UserEntity user, Long id) {
        var inspection = requireLocked(user, id);
        if (inspection.getStatus() != Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inspection is not editable");
        }
        return inspection;
    }

    /** Gebundene Fassung; vor dem Start eine Vorschau der aktuell veröffentlichten Fassung. */
    private List<CriterionEntity> criteria(InspectionEntity inspection) {
        var version = inspection.getChecklistVersion() != null
            ? inspection.getChecklistVersion() : checklists.currentPublished().orElse(null);
        return version == null ? List.of() : checklists.criteria(version);
    }

    private InspectionView toView(InspectionEntity inspection) {
        var flat = inspection.getFlat();
        var criteria = criteria(inspection).stream()
            .map(c -> new CriterionView(c.getId(), c.getArea(), c.getTitle(), c.getDescription(), c.getPosition()))
            .toList();
        var rated = findings.findByInspectionId(inspection.getId()).stream()
            .map(f -> new FindingView(f.getCriterion().getId(), f.getRating(), f.getSeverity(), f.getNote(),
                f.isBuildingCauseSuspected()))
            .sorted(Comparator.comparing(FindingView::criterionId))
            .toList();
        var inspector = inspection.getInspector();
        return new InspectionView(inspection.getId(), flat.getLabel(), flat.getResidence().getName(),
            flat.getCapacity(), inspection.getRound(), inspection.getStatus(), inspection.getOverallResult(),
            inspection.getInspectedAt(), inspector == null ? null : inspector.getName(), criteria, rated);
    }

    /** Die Kontrolle gehört zu einem Zeitraum, der noch nicht begonnen hat. */
    public static class PeriodNotStartedException extends RuntimeException {}

    /**
     * Zeitraum und Uhrzeit des angekündigten Zeitraums; leer bei Kontrollen ohne Zeitraum. {@code periodKind}
     * unterscheidet reguläre von individuellen Kontrollen (US-18b).
     */
    public record InspectionSummary(Long id, String flatLabel, String residenceName, Status status, int round,
        LocalDate startsOn, LocalDate endsOn, LocalTime windowStart, LocalTime windowEnd,
        InspectionPeriodEntity.Kind periodKind) {}

    public record InspectionView(Long id, String flatLabel, String residenceName, int capacity, int round,
        Status status, Result overallResult, Instant inspectedAt, String inspectorName,
        List<CriterionView> criteria, List<FindingView> findings) {}

    public record CriterionView(Long id, String area, String title, String description, int position) {}

    public record FindingView(Long criterionId, Rating rating, Severity severity, String note,
        boolean buildingCauseSuspected) {}

    /** {@code buildingCauseSuspected} ist optional; fehlt es, gilt {@code false}. */
    public record FindingRequest(Rating rating, Severity severity, String note, Boolean buildingCauseSuspected) {}
}
