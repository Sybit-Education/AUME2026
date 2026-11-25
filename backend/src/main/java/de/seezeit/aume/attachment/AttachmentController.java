package de.seezeit.aume.attachment;

import java.net.URI;
import java.security.Principal;
import de.seezeit.aume.attachment.AttachmentService.AttachmentView;
import de.seezeit.aume.attachment.AttachmentService.Download;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Entwicklungs-Upload. Ohne Profil dev ist der Endpunkt nicht registriert. */
@RestController
@Profile("dev")
public class AttachmentController {
    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping(path = "/api/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentView> upload(Principal principal, @RequestParam("file") MultipartFile file) {
        var view = attachments.store(principal.getName(), file);
        return ResponseEntity.created(URI.create("/api/attachments/" + view.id())).body(view);
    }

    @GetMapping("/api/attachments/{id}")
    public ResponseEntity<Resource> download(Principal principal, @PathVariable Long id) {
        Download download = attachments.load(principal.getName(), id);
        return ResponseEntity.ok()
            .contentType(mediaType(download.contentType()))
            .contentLength(download.sizeBytes())
            .cacheControl(CacheControl.noStore())
            .body(download.body());
    }

    /** Fachfehler mit stabilem Code, den die Oberfläche übersetzen kann. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorView(e.getReason()));
    }

    private static MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public record ErrorView(String code) {}
}
