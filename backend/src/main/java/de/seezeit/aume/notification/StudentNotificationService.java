package de.seezeit.aume.notification;

import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import de.seezeit.aume.planning.InspectionAnnouncementEntity;
import de.seezeit.aume.standard.ChecklistPdfService;
import de.seezeit.aume.standard.ChecklistPdfService.ChecklistPdf;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentNotificationService {
    private static final DateTimeFormatter WINDOW_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final NotificationRepository notifications;
    private final AppUserService users;
    private final ChecklistPdfService checklistPdfs;
    private final Clock clock;

    StudentNotificationService(NotificationRepository notifications, AppUserService users, ChecklistPdfService checklistPdfs,
            @Qualifier("inspectionNotificationClock") Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.checklistPdfs = checklistPdfs;
        this.clock = clock;
    }

    public Page<NotificationView> list(String userKey, int page, int size, boolean canReadChecklist) {
        var userId = users.requireUser(userKey).getId();
        return notifications.pageVisibleInspectionNotifications(userId, LocalDate.now(clock), PageRequest.of(page, size))
            .map(notification -> NotificationView.from(notification,
                canReadChecklist && hasChecklistPdf(notification.getInspectionNotification())));
    }

    public Optional<ChecklistPdf> checklistPdf(String userKey, long id, Locale locale) {
        var userId = users.requireUser(userKey).getId();
        return notifications.findVisibleInspectionNotification(userId, LocalDate.now(clock), id)
            .map(NotificationEntity::getInspectionNotification)
            .filter(this::hasChecklistPdf)
            .flatMap(message -> checklistPdfs.render(message.getChecklistVersion(), locale));
    }

    private boolean hasChecklistPdf(InspectionNotificationEntity message) {
        return message.getKind() == Kind.ANNOUNCEMENT && checklistPdfs.isAvailable(message.getChecklistVersion());
    }

    public UnreadCount unreadCount(String userKey) {
        var userId = users.requireUser(userKey).getId();
        return new UnreadCount(notifications.countUnreadInspectionNotifications(userId, LocalDate.now(clock)));
    }

    @Transactional
    public boolean markRead(String userKey, long id) {
        var userId = users.requireUser(userKey).getId();
        var now = ZonedDateTime.now(clock);
        var today = now.toLocalDate();
        return notifications.markVisibleInspectionNotificationRead(userId, today, id, now.toInstant()) > 0
            || notifications.findVisibleInspectionNotification(userId, today, id).isPresent();
    }

    public record UnreadCount(long count) {}

    public record NotificationView(Long id, Kind type, Instant createdAt, Instant occurredAt, Instant readAt,
            String flatLabel, LocalDate startsOn, LocalDate endsOn, String windowStart, String windowEnd,
            boolean hasChecklistPdf, InspectionAnnouncementEntity.Kind announcementKind, String announcementMessage) {
        private static NotificationView from(NotificationEntity notification, boolean hasChecklistPdf) {
            var message = notification.getInspectionNotification();
            var sourceAnnouncement = message.getSourceAnnouncement();
            var occurredAt = message.getKind() == Kind.ANNOUNCEMENT
                ? message.getAnnouncementRevisionAt() : notification.getCreatedAt();
            return new NotificationView(notification.getId(), message.getKind(), notification.getCreatedAt(),
                occurredAt, notification.getReadAt(), message.getInspection().getFlat().getLabel(),
                message.getStartsOn(), message.getEndsOn(), format(message.getWindowStart()),
                format(message.getWindowEnd()), hasChecklistPdf, message.getAnnouncementKind(),
                sourceAnnouncement == null ? null : sourceAnnouncement.getMessage());
        }

        private static String format(LocalTime time) {
            return time == null ? null : WINDOW_TIME.format(time);
        }
    }
}
