package de.seezeit.aume.planning;

import java.security.Principal;
import de.seezeit.aume.planning.InspectionPlanningService.CounterRequest;
import de.seezeit.aume.planning.InspectionPlanningService.CreatePeriodRequest;
import de.seezeit.aume.planning.InspectionPlanningService.OptionsView;
import de.seezeit.aume.planning.InspectionPlanningService.PeriodView;
import de.seezeit.aume.planning.InspectionPlanningService.UpdatePeriodRequest;
import de.seezeit.aume.planning.InspectionPlanningService.VersionRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/inspection-periods")
public class InspectionPlanningController {
    private static final String PLAN = "hasAuthority('INSPECTION_PLAN')";
    private static final String RESPOND = "hasAuthority('INSPECTION_PERIOD_RESPOND')";

    private final InspectionPlanningService planning;

    public InspectionPlanningController(InspectionPlanningService planning) {
        this.planning = planning;
    }

    @GetMapping("/options")
    @PreAuthorize(PLAN)
    public OptionsView options(Principal principal) {
        return planning.options(principal.getName());
    }

    @GetMapping
    @PreAuthorize(PLAN)
    public PagedModel<PeriodView> list(Principal principal, Pageable pageable) {
        return new PagedModel<>(planning.list(principal.getName(), pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PLAN)
    public PeriodView create(Principal principal, @RequestBody CreatePeriodRequest request) {
        return planning.create(principal.getName(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(PLAN)
    public PeriodView update(Principal principal, @PathVariable Long id, @RequestBody UpdatePeriodRequest request) {
        return planning.update(principal.getName(), id, request);
    }

    @PostMapping("/{id}/accept-counter")
    @PreAuthorize(PLAN)
    public PeriodView acceptCounter(Principal principal, @PathVariable Long id, @RequestBody VersionRequest request) {
        return planning.acceptCounter(principal.getName(), id, request.version());
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(PLAN)
    public void withdraw(Principal principal, @PathVariable Long id, @RequestBody VersionRequest request) {
        planning.withdraw(principal.getName(), id, request.version());
    }

    @GetMapping("/assigned")
    @PreAuthorize(RESPOND)
    public PagedModel<PeriodView> assigned(Principal principal, Pageable pageable) {
        return new PagedModel<>(planning.assigned(principal.getName(), pageable));
    }

    @PostMapping("/{id}/agree")
    @PreAuthorize(RESPOND)
    public PeriodView agree(Principal principal, @PathVariable Long id, @RequestBody VersionRequest request) {
        return planning.agree(principal.getName(), id, request.version());
    }

    @PostMapping("/{id}/counter")
    @PreAuthorize(RESPOND)
    public PeriodView counter(Principal principal, @PathVariable Long id, @RequestBody CounterRequest request) {
        return planning.counter(principal.getName(), id, request);
    }

    /** Fachfehler mit stabilem Code, den die Oberfläche übersetzt. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorView(e.getReason()));
    }

    /** Zwei gleichzeitige Änderungen: Die zweite bekommt denselben Fehler wie bei einer veralteten Version. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorView> changedConcurrently() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorView("period_changed"));
    }

    public record ErrorView(String code) {}
}
