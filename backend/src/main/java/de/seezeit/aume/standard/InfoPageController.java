package de.seezeit.aume.standard;

import java.security.Principal;
import de.seezeit.aume.standard.InfoPageService.InfoPagesRequest;
import de.seezeit.aume.standard.InfoPageService.InfoPagesView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InfoPageController {
    private final InfoPageService infoPages;

    public InfoPageController(InfoPageService infoPages) {
        this.infoPages = infoPages;
    }

    @GetMapping("/api/info-pages")
    @PreAuthorize("hasAuthority('INFO_PAGE_READ')")
    public InfoPagesView read() {
        return infoPages.read();
    }

    @PutMapping("/api/info-pages")
    @PreAuthorize("hasAuthority('INFO_PAGE_EDIT')")
    public InfoPagesView update(Principal principal, @RequestBody InfoPagesRequest request) {
        return infoPages.update(principal.getName(), request);
    }

    /** Fachfehler mit stabilem Code, den die Oberfläche übersetzt. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorView(e.getReason()));
    }

    public record ErrorView(String code) {}
}
