package de.seezeit.aume.notification;

import de.seezeit.aume.notification.StudentNotificationService.NotificationView;
import de.seezeit.aume.notification.StudentNotificationService.UnreadCount;
import java.security.Principal;
import org.springframework.data.web.PagedModel;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ResponseEntity<PagedModel<NotificationView>> list(Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new PagedModel<>(notifications.list(principal.getName(), page, size)));
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
