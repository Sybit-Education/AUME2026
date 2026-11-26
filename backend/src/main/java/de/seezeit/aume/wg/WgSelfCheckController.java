package de.seezeit.aume.wg;

import java.security.Principal;
import de.seezeit.aume.wg.WgSelfCheckService.SelfCheckView;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Selbstcheck der eigenen WG mit der Checkliste (US-84). 404 {@code not_member} ohne WG. */
@RestController
@RequestMapping("/api/wg/self-check")
@PreAuthorize("hasAuthority('WG_MEMBERSHIP') and hasAuthority('CHECKLIST_READ')")
public class WgSelfCheckController {
    private final WgSelfCheckService selfCheck;

    public WgSelfCheckController(WgSelfCheckService selfCheck) {
        this.selfCheck = selfCheck;
    }

    @GetMapping
    public ResponseEntity<SelfCheckView> view(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(selfCheck.view(principal.getName()));
    }

    @PutMapping("/{criterionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void check(Principal principal, @PathVariable Long criterionId) {
        selfCheck.check(principal.getName(), criterionId);
    }

    @DeleteMapping("/{criterionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uncheck(Principal principal, @PathVariable Long criterionId) {
        selfCheck.uncheck(principal.getName(), criterionId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(Principal principal) {
        selfCheck.reset(principal.getName());
    }

    /** Fachfehler mit stabilem Code, den die Oberfläche übersetzt. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorView(e.getReason()));
    }

    public record ErrorView(String code) {}
}
