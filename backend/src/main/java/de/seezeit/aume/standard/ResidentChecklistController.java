package de.seezeit.aume.standard;

import java.util.Locale;
import de.seezeit.aume.standard.ResidentChecklistService.ChecklistView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Checkliste für Bewohnende in der angefragten Sprache (Deutsch oder Englisch). */
@RestController
public class ResidentChecklistController {
    private final ResidentChecklistService checklists;

    public ResidentChecklistController(ResidentChecklistService checklists) {
        this.checklists = checklists;
    }

    @GetMapping("/api/checklist")
    @PreAuthorize("hasAuthority('CHECKLIST_READ')")
    public ResponseEntity<ChecklistView> current(Locale locale) {
        var checklist = checklists.current(locale)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "checklist_not_published"));
        return ResponseEntity.ok().headers(headers -> headers.setContentLanguage(locale)).body(checklist);
    }

    /** Fachfehler mit stabilem Code, den die Oberfläche übersetzt. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorView(e.getReason()));
    }

    public record ErrorView(String code) {}
}
