package de.seezeit.aume.wg;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Locale;
import de.seezeit.aume.wg.WgTaskService.TaskRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/wg/tasks")
@PreAuthorize("hasAuthority('WG_MEMBERSHIP')")
public class WgTaskController {
    private final WgTaskService tasks;

    public WgTaskController(WgTaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public ResponseEntity<WgPlanView> plan(Principal principal, Locale locale,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return ResponseEntity.ok().headers(headers -> headers.setContentLanguage(locale))
            .body(tasks.plan(principal.getName(), week, locale));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(Principal principal, @RequestBody TaskRequest request) {
        tasks.create(principal.getName(), request);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(Principal principal, @PathVariable Long id, @RequestBody TaskRequest request) {
        tasks.update(principal.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable Long id) {
        tasks.delete(principal.getName(), id);
    }

    @PutMapping("/{id}/completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(Principal principal, @PathVariable Long id) {
        tasks.complete(principal.getName(), id);
    }

    @DeleteMapping("/{id}/completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(Principal principal, @PathVariable Long id) {
        tasks.undo(principal.getName(), id);
    }

    /** Returns a stable error code for the UI to translate. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorView(e.getReason()));
    }

    public record ErrorView(String code) {}
}
