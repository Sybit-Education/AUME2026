package de.seezeit.aume.notification;

import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
    private final Clock clock;

    StudentNotificationService(NotificationRepository notifications, AppUserService users,
            @Qualifier("inspectionNotificationClock") Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    public Page<NotificationView> list(String userKey, int page, int size) {
        var userId = users.requireUser(userKey).getId();
        return notifications.pageVisibleInspectionNotifications(userId, LocalDate.now(clock), PageRequest.of(page, size))
            .map(NotificationView::from);
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
            String flatLabel, LocalDate startsOn, LocalDate endsOn, String windowStart, String windowEnd) {
        private static NotificationView from(NotificationEntity notification) {
            var message = notification.getInspectionNotification();
            var occurredAt = message.getKind() == Kind.ANNOUNCEMENT
                ? message.getAnnouncementRevisionAt() : notification.getCreatedAt();
            return new NotificationView(notification.getId(), message.getKind(), notification.getCreatedAt(),
                occurredAt, notification.getReadAt(), message.getInspection().getFlat().getLabel(),
                message.getStartsOn(), message.getEndsOn(), format(message.getWindowStart()),
                format(message.getWindowEnd()));
        }

        private static String format(LocalTime time) {
            return time == null ? null : WINDOW_TIME.format(time);
        }
    }
}
