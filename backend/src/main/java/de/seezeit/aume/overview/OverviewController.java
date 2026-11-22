package de.seezeit.aume.overview;

import java.security.Principal;
import de.seezeit.aume.overview.OverviewService.CaretakerOverview;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OverviewController {
    private final OverviewService overview;

    public OverviewController(OverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/api/overview/caretaker")
    @PreAuthorize("hasAuthority('INSPECTION_OVERVIEW_READ')")
    public CaretakerOverview caretaker(Principal principal) {
        return overview.caretakerOverview(principal.getName());
    }
}
