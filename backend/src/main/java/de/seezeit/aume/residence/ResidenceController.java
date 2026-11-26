package de.seezeit.aume.residence;

import java.security.Principal;
import de.seezeit.aume.residence.ResidenceView.StaffView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ResidenceController {
    private final ResidenceService residences;

    public ResidenceController(ResidenceService residences) {
        this.residences = residences;
    }

    @GetMapping("/api/residences")
    @PreAuthorize("hasAuthority('RESIDENCE_READ')")
    public PagedModel<?> list(Principal principal, Pageable pageable) {
        return new PagedModel<>(residences.list(principal.getName(), pageable));
    }

    @GetMapping("/api/residences/my-assignments")
    @PreAuthorize("hasAuthority('RESIDENCE_READ')")
    public PagedModel<StaffResidenceView> myAssignments(Principal principal, Pageable pageable) {
        return new PagedModel<>(residences.listCaretakerAssignments(principal.getName(), pageable));
    }

    @GetMapping("/api/caretakers")
    @PreAuthorize("hasAuthority('RESIDENCE_ASSIGN_STAFF')")
    public PagedModel<StaffView> caretakers(Pageable pageable) {
        return new PagedModel<>(residences.listCaretakers(pageable));
    }

    @PutMapping("/api/residences/{id}/staff")
    @PreAuthorize("hasAuthority('RESIDENCE_ASSIGN_STAFF')")
    public ResidenceView assignStaff(Principal principal, @PathVariable Long id,
                                     @RequestBody StaffAssignmentRequest request) {
        return residences.assignStaff(principal.getName(), id, request);
    }

    /** Fachfehler dieses Controllers mit stabilem Code ({@code code}), den die Oberfläche übersetzt. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorView(e.getReason()));
    }

    public record ErrorView(String code) {}
}
