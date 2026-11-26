package de.seezeit.aume.inspection;

import java.security.Principal;
import java.util.Locale;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** PDF-Download des Protokolls einer abgeschlossenen Kontrolle (#11). */
@RestController
@PreAuthorize("hasAuthority('INSPECTION_PERFORM')")
public class ProtocolPdfController {
    private final ProtocolPdfService protocols;

    public ProtocolPdfController(ProtocolPdfService protocols) {
        this.protocols = protocols;
    }

    @GetMapping(value = "/api/inspections/{id}/protocol.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(Principal principal, @PathVariable Long id, Locale locale) {
        var protocol = protocols.render(principal.getName(), id, locale);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(protocol.fileName()).build().toString())
            // Protokolle enthalten personenbezogene Notizen und sollen nicht in Zwischenspeichern landen.
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.APPLICATION_PDF)
            .body(protocol.content());
    }
}
