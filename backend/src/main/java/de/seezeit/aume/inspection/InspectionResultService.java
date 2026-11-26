package de.seezeit.aume.inspection;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import de.seezeit.aume.inspection.FindingEntity.Rating;
import de.seezeit.aume.inspection.FindingEntity.Severity;
import de.seezeit.aume.inspection.InspectionEntity.Result;
import de.seezeit.aume.inspection.InspectionEntity.Status;
import de.seezeit.aume.standard.ChecklistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schnittstelle des Moduls {@code inspection} für die Ansicht der Bewohnenden: veröffentlichte
 * Kontrollergebnisse einer WG mit ausgefüllter Checkliste.
 *
 * <p>Der Service prüft keine Rechte und kennt keine Bewohnenden. Welche WG ein Nutzer sehen darf
 * (über {@code memberships}) und ab welchem Zeitpunkt, entscheidet der Aufrufer.
 *
 * <p>Sichtbar ist eine Kontrolle ab {@link #VISIBLE_TO_RESIDENTS}. "Kontrolle abschließen" durch die
 * Objektbetreuung setzt diesen Status direkt (Teamentscheidung 24.11.2026).
 */
@Service
@Transactional(readOnly = true)
public class InspectionResultService {
    /** Ab diesem Status sehen Bewohnende ein Ergebnis. */
    public static final Status VISIBLE_TO_RESIDENTS = Status.PUBLISHED;

    private final ResidentResultRepository inspections;
    private final FindingRepository findings;
    private final ChecklistService checklists;

    InspectionResultService(ResidentResultRepository inspections, FindingRepository findings,
            ChecklistService checklists) {
        this.inspections = inspections;
        this.findings = findings;
        this.checklists = checklists;
    }

    /** Alle sichtbaren Ergebnisse der WG, neueste zuerst. */
    public List<ResidentResult> resultsForFlat(Long flatId) {
        return inspections.findByFlatIdAndStatusOrderByPublishedAtDesc(flatId, VISIBLE_TO_RESIDENTS).stream()
            .map(this::toResult).toList();
    }

    /**
     * Sichtbare Ergebnisse der WG, die ab {@code notBefore} veröffentlicht wurden, neueste zuerst. Damit
     * kann der Aufrufer zum Beispiel nur Ergebnisse ab dem Einzug eines Bewohnenden zeigen.
     */
    public List<ResidentResult> resultsForFlat(Long flatId, Instant notBefore) {
        return inspections.findByFlatIdAndStatusAndPublishedAtGreaterThanEqualOrderByPublishedAtDesc(
                flatId, VISIBLE_TO_RESIDENTS, notBefore).stream()
            .map(this::toResult).toList();
    }

    /** Zeitpunkt, zu dem die WG zuletzt ein Kontrollergebnis bekommen hat; leer ohne Ergebnis. */
    public Optional<Instant> latestPublication(Long flatId) {
        return inspections.findFirstByFlatIdAndStatusOrderByPublishedAtDesc(flatId, VISIBLE_TO_RESIDENTS)
            .map(InspectionEntity::getPublishedAt);
    }

    public boolean hasVisibleResult(Long inspectionId, Long flatId, Instant notBefore) {
        return inspections.existsByIdAndFlatIdAndStatusAndPublishedAtGreaterThanEqual(
            inspectionId, flatId, VISIBLE_TO_RESIDENTS, notBefore);
    }

    private ResidentResult toResult(InspectionEntity inspection) {
        var criteria = inspection.getChecklistVersion() == null ? List.<ResultCriterion>of()
            : checklists.criteria(inspection.getChecklistVersion()).stream()
                .map(c -> new ResultCriterion(c.getId(), c.getArea(), c.getTitle(), c.getDescription(),
                    c.getPosition()))
                .toList();
        var rated = findings.findByInspectionId(inspection.getId()).stream()
            .map(f -> new ResultFinding(f.getCriterion().getId(), f.getRating(), f.getSeverity(), f.getNote(),
                f.isBuildingCauseSuspected()))
            .sorted(Comparator.comparing(ResultFinding::criterionId))
            .toList();
        var flat = inspection.getFlat();
        return new ResidentResult(inspection.getId(), flat.getId(), flat.getLabel(), inspection.getRound(),
            inspection.isHandover(), inspection.getOverallResult(), inspection.getInspectedAt(),
            inspection.getPublishedAt(), criteria, rated);
    }

    /**
     * Ergebnis einer Kontrolle für Bewohnende. Enthält bewusst keine Angaben zur prüfenden Person.
     * {@code round} 0 ist die Erstkontrolle, 1 und 2 sind Nachkontrollen.
     */
    public record ResidentResult(Long inspectionId, Long flatId, String flatLabel, int round, boolean handover,
        Result overallResult, Instant inspectedAt, Instant publishedAt, List<ResultCriterion> criteria,
        List<ResultFinding> findings) {}

    public record ResultCriterion(Long id, String area, String title, String description, int position) {}

    public record ResultFinding(Long criterionId, Rating rating, Severity severity, String note,
        boolean buildingCauseSuspected) {}
}
