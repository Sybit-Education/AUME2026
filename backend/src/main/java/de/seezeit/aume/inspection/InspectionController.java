package de.seezeit.aume.inspection;

import java.security.Principal;
import de.seezeit.aume.inspection.InspectionEntity.Result;
import de.seezeit.aume.inspection.InspectionService.FindingRequest;
import de.seezeit.aume.inspection.InspectionService.InspectionSummary;
import de.seezeit.aume.inspection.InspectionService.InspectionView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Kontrolle durch die Objektbetreuung: Checkliste abarbeiten, Mängel erfassen, abschließen. */
@RestController
@RequestMapping("/api/inspections")
@PreAuthorize("hasAuthority('INSPECTION_PERFORM')")
public class InspectionController {
    private final InspectionService inspections;

    public InspectionController(InspectionService inspections) {
        this.inspections = inspections;
    }

    @GetMapping
    public PagedModel<InspectionSummary> list(Principal principal, @PageableDefault(sort = "id") Pageable pageable) {
        return new PagedModel<>(inspections.listOpen(principal.getName(), pageable));
    }

    @GetMapping("/{id}")
    public InspectionView get(Principal principal, @PathVariable Long id) {
        return inspections.view(principal.getName(), id);
    }

    @PostMapping("/{id}/start")
    public InspectionView start(Principal principal, @PathVariable Long id) {
        return inspections.start(principal.getName(), id);
    }

    @PutMapping("/{id}/findings/{criterionId}")
    public InspectionView rate(Principal principal, @PathVariable Long id, @PathVariable Long criterionId,
            @RequestBody FindingRequest request) {
        return inspections.rate(principal.getName(), id, criterionId, request);
    }

    @DeleteMapping("/{id}/findings/{criterionId}")
    public InspectionView clear(Principal principal, @PathVariable Long id, @PathVariable Long criterionId) {
        return inspections.clear(principal.getName(), id, criterionId);
    }

    /** "Bereich in Ordnung" mit {@code area}, "Alles in Ordnung" ohne. */
    @PostMapping("/{id}/mark-ok")
    public InspectionView markOk(Principal principal, @PathVariable Long id,
            @RequestBody(required = false) MarkOkRequest request) {
        return inspections.markOk(principal.getName(), id, request == null ? null : request.area());
    }

    @PostMapping("/{id}/finalize")
    public InspectionView finalizeInspection(Principal principal, @PathVariable Long id,
            @RequestBody FinalizeRequest request) {
        return inspections.finalizeInspection(principal.getName(), id, request.overallResult());
    }

    public record MarkOkRequest(String area) {}

    public record FinalizeRequest(Result overallResult) {}

    /** 409 mit Code, damit das Frontend „erst ab Beginn des Zeitraums“ statt eines allgemeinen Konflikts zeigt. */
    @ExceptionHandler(InspectionService.PeriodNotStartedException.class)
    public ResponseEntity<ErrorView> periodNotStarted() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorView("period_not_started"));
    }

    public record ErrorView(String code) {}
}
