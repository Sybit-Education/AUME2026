package de.seezeit.aume.inspection;

import java.security.Principal;
import java.util.List;
import de.seezeit.aume.inspection.PreviousFindingsService.PreviousInspection;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Vorgeschichte der WG einer Kontrolle (US-51). */
@RestController
@PreAuthorize("hasAuthority('INSPECTION_PERFORM')")
public class PreviousFindingsController {
    private final PreviousFindingsService previous;

    public PreviousFindingsController(PreviousFindingsService previous) {
        this.previous = previous;
    }

    @GetMapping("/api/inspections/{id}/previous-findings")
    public List<PreviousInspection> list(Principal principal, @PathVariable Long id) {
        return previous.previous(principal.getName(), id);
    }
}
