package de.seezeit.aume.residence;

import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/memberships")
@PreAuthorize("hasAuthority('WG_MEMBERSHIP')")
public class MembershipController {
    private final MembershipService memberships;

    public MembershipController(MembershipService memberships) {
        this.memberships = memberships;
    }

    /** Returns {@code 204} if the user has no flat. */
    @GetMapping("/me")
    public ResponseEntity<MembershipView> current(Principal principal) {
        return memberships.current(principal.getName()).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipView join(Principal principal, @RequestBody JoinRequest request) {
        return memberships.join(principal.getName(), request.code());
    }

    @PostMapping("/me/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(Principal principal) {
        memberships.leave(principal.getName());
    }

    /** Returns a stable error code for the UI to translate. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorView(e.getReason()));
    }

    public record JoinRequest(String code) {}

    public record ErrorView(String code) {}
}
