package de.seezeit.aume.overview;

import java.security.Principal;
import de.seezeit.aume.overview.InspectionOverviewService.Counts;
import de.seezeit.aume.overview.InspectionOverviewService.ResidenceDetail;
import de.seezeit.aume.overview.InspectionOverviewService.ResidenceRow;
import de.seezeit.aume.overview.InspectionOverviewService.State;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspection-overview")
@PreAuthorize("hasAuthority('CLERK_INSPECTION_OVERVIEW_READ')")
public class InspectionOverviewController {
    private final InspectionOverviewService overview;

    public InspectionOverviewController(InspectionOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping
    public PagedModel<ResidenceRow> list(Principal principal, @RequestParam(required = false) State state,
            @RequestParam(required = false) InspectionPeriodEntity.Kind kind, Pageable pageable) {
        return new PagedModel<>(overview.overview(principal.getName(), state, kind, pageable));
    }

    @GetMapping("/counts")
    public Counts counts(Principal principal) {
        return overview.counts(principal.getName());
    }

    @GetMapping("/{residenceId}")
    public ResidenceDetail detail(Principal principal, @PathVariable Long residenceId) {
        return overview.detail(principal.getName(), residenceId);
    }
}
