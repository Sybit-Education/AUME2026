package de.seezeit.aume.planning;

import java.security.Principal;
import java.util.List;
import de.seezeit.aume.planning.InspectionAnnouncementService.AnnounceRequest;
import de.seezeit.aume.planning.InspectionAnnouncementService.AnnouncementView;
import de.seezeit.aume.planning.InspectionAnnouncementService.DraftView;
import de.seezeit.aume.planning.InspectionAnnouncementService.RescheduleRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Ankündigung durch die Sachbearbeitung (US-18). Gleiches Privileg wie die Planung. */
@RestController
@RequestMapping("/api/inspection-periods/{id}")
@PreAuthorize("hasAuthority('INSPECTION_PLAN')")
public class InspectionAnnouncementController {
    private final InspectionAnnouncementService announcements;

    public InspectionAnnouncementController(InspectionAnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping("/announcement-draft")
    public DraftView draft(Principal principal, @PathVariable Long id) {
        return announcements.draft(principal.getName(), id);
    }

    @PostMapping("/announce")
    public AnnouncementView announce(Principal principal, @PathVariable Long id, @RequestBody AnnounceRequest request) {
        return announcements.announce(principal.getName(), id, request);
    }

    @PostMapping("/reschedule")
    public AnnouncementView reschedule(Principal principal, @PathVariable Long id,
            @RequestBody RescheduleRequest request) {
        return announcements.reschedule(principal.getName(), id, request);
    }

    @GetMapping("/announcements")
    public List<AnnouncementView> history(Principal principal, @PathVariable Long id) {
        return announcements.history(principal.getName(), id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorView(e.getReason()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorView> changedConcurrently() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorView("period_changed"));
    }

    public record ErrorView(String code) {}
}
