package de.seezeit.aume.inspection;

import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Anzeige eines Mangel-Fotos für alle, die für die Anlage zuständig sind (US-35). Nur im Profil {@code dev}. */
@RestController
@Profile("dev")
@PreAuthorize("hasAuthority('INSPECTION_PERFORM')")
public class FindingPhotoController {
    private final FindingPhotoFileService photos;

    FindingPhotoController(FindingPhotoFileService photos) {
        this.photos = photos;
    }

    @GetMapping("/api/inspections/{id}/photos/{photoId}")
    public ResponseEntity<Resource> show(Principal principal, @PathVariable Long id, @PathVariable Long photoId) {
        var photo = photos.load(principal.getName(), id, photoId);
        // Auch wenn ein Foto direkt im Browser geöffnet wird, darf es kein Skript ausführen: sandbox, kein
        // Typ-Raten und nur erlaubte Rasterformate; alles andere geht als Download raus.
        return ResponseEntity.ok()
            .contentType(mediaType(photo.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
            .header("Content-Security-Policy", "sandbox; default-src 'none'")
            .header("X-Content-Type-Options", "nosniff")
            .contentLength(photo.sizeBytes())
            .cacheControl(CacheControl.noStore())
            .body(photo.body());
    }

    private static MediaType mediaType(String contentType) {
        if (!InspectionService.isPhotoType(contentType)) return MediaType.APPLICATION_OCTET_STREAM;
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
