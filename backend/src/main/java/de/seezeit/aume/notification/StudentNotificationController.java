package de.seezeit.aume.notification;

import de.seezeit.aume.notification.StudentNotificationService.NotificationView;
import de.seezeit.aume.notification.StudentNotificationService.UnreadCount;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Locale;
import org.springframework.core.io.Resource;
import org.springframework.data.web.PagedModel;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/notifications")
@PreAuthorize("hasRole('STUDENT')")
public class StudentNotificationController {
    private final StudentNotificationService notifications;

    public StudentNotificationController(StudentNotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ResponseEntity<PagedModel<NotificationView>> list(Authentication authentication,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
        }
        var canReadChecklist = authentication.getAuthorities().stream()
            .anyMatch(authority -> "CHECKLIST_READ".equals(authority.getAuthority()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new PagedModel<>(notifications.list(authentication.getName(), page, size, canReadChecklist)));
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<Resource> attachment(Principal principal, @PathVariable long id) {
        return notifications.attachment(principal.getName(), id).map(attachment ->
            ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(attachment.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(attachmentMediaType(attachment.contentType()))
                .contentLength(attachment.sizeBytes())
                .body(attachment.body())
        ).orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build());
    }

    private static MediaType attachmentMediaType(String uploadedType) {
        if (uploadedType != null && uploadedType.codePoints().noneMatch(Character::isISOControl)) {
            try {
                var mediaType = MediaType.parseMediaType(uploadedType);
                if (mediaType.isConcrete()) return new MediaType(mediaType.getType(), mediaType.getSubtype());
            } catch (IllegalArgumentException ignored) {
                // Der beim Upload gemeldete Typ ist unverifiziert.
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @GetMapping(value = "/{id}/checklist.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('CHECKLIST_READ')")
    public ResponseEntity<byte[]> checklistPdf(Principal principal, @PathVariable long id, Locale locale) {
        return notifications.checklistPdf(principal.getName(), id, locale)
            .map(pdf -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_LANGUAGE, pdf.language().toLanguageTag())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(pdf.fileName()).build().toString())
                .body(pdf.content()))
            .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCount> unreadCount(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(notifications.unreadCount(principal.getName()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(Principal principal, @PathVariable long id) {
        var response = notifications.markRead(principal.getName(), id)
            ? ResponseEntity.noContent() : ResponseEntity.notFound();
        return response.cacheControl(CacheControl.noStore()).build();
    }
}
