package de.seezeit.aume.notification;

import de.seezeit.aume.inspection.InspectionNotificationSource;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import de.seezeit.aume.residence.MembershipRepository;
import de.seezeit.aume.standard.ChecklistService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionNotificationDeliveryService {
    private final InspectionNotificationSource source;
    private final InspectionNotificationRepository messages;
    private final NotificationRepository notifications;
    private final MembershipRepository memberships;
    private final ChecklistService checklists;
    private final Clock clock;

    InspectionNotificationDeliveryService(InspectionNotificationSource source,
            InspectionNotificationRepository messages, NotificationRepository notifications,
            MembershipRepository memberships, ChecklistService checklists,
            @Qualifier("inspectionNotificationClock") Clock clock) {
        this.source = source;
        this.messages = messages;
        this.notifications = notifications;
        this.memberships = memberships;
        this.checklists = checklists;
        this.clock = clock;
    }

    @Transactional
    public void deliver(long inspectionId, Kind kind) {
        var inspection = source.lockCandidate(inspectionId, LocalDate.now(clock)).orElse(null);
        if (inspection == null) return;

        var now = ZonedDateTime.now(clock);
        var period = inspection.getPeriod();
        if (period.getEndsOn().isBefore(now.toLocalDate()) || !isDue(kind, period.getStartsOn(), now)) return;

        var announcedAt = period.getAnnouncedAt();
        var rescheduledAt = period.getRescheduledAt();
        var revisionAt = rescheduledAt != null && rescheduledAt.isAfter(announcedAt) ? rescheduledAt : announcedAt;
        if (messages.findByInspectionIdAndAnnouncementRevisionAtAndKind(inspectionId, revisionAt, kind).isPresent()) {
            return;
        }

        var checklist = kind == Kind.ANNOUNCEMENT ? checklists.currentPublished().orElse(null) : null;
        var message = messages.save(new InspectionNotificationEntity(inspection, kind, revisionAt,
            period.getStartsOn(), period.getEndsOn(), period.getWindowStart(), period.getWindowEnd(), checklist));
        var title = kind == Kind.ANNOUNCEMENT ? "Ankündigung" : "Erinnerung";
        var recipients = memberships.findActiveInFlat(inspection.getFlat().getId(), now.toLocalDate()).stream()
            .map(membership -> membership.getUser())
            .distinct()
            .map(user -> new NotificationEntity(user, message, title, null))
            .toList();
        notifications.saveAll(recipients);
    }

    static boolean isDue(Kind kind, LocalDate startsOn, ZonedDateTime now) {
        return switch (kind) {
            case ANNOUNCEMENT -> true;
            case REMINDER_7_DAYS -> now.toLocalDate().equals(startsOn.minusDays(7));
            case REMINDER_1_DAY -> now.toLocalDate().equals(startsOn.minusDays(1));
        };
    }
}
