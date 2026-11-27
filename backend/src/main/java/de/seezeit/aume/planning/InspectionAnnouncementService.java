package de.seezeit.aume.planning;

import static de.seezeit.aume.planning.InspectionPlanningService.badRequest;
import static de.seezeit.aume.planning.InspectionPlanningService.conflict;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import de.seezeit.aume.attachment.AttachmentEntity;
import de.seezeit.aume.attachment.AttachmentRepository;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionSchedulingService;
import de.seezeit.aume.planning.InspectionAnnouncementEntity.Kind;
import de.seezeit.aume.planning.InspectionPeriodEntity.Status;
import de.seezeit.aume.residence.ResidenceView.StaffView;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.residence.StaffKind;
import de.seezeit.aume.user.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Abgestimmte Kontrolle an alle WGs des Zeitraums ankündigen (bei individuellen Kontrollen nur die ausgewählten) und angekündigte Termine ändern (US-18).
 * Setzt nur {@code announcedAt}/{@code rescheduledAt} und schreibt den Versandeintrag. Die Nachrichten an die
 * WGs erzeugt der Scheduler im Notification-Modul.
 */
@Service
@Transactional(readOnly = true)
public class InspectionAnnouncementService {
    static final int MAX_MESSAGE_LENGTH = 5000;
    private static final int MAX_ATTACHMENT_NAME_LENGTH = 255;
    /** E-Mail ist bewusst noch nicht umgesetzt (offene Frage der Story). */
    private static final String CHANNELS = "IN_APP";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final InspectionPeriodRepository periods;
    private final InspectionAnnouncementRepository announcements;
    private final StaffAssignmentRepository staff;
    private final AttachmentRepository attachments;
    private final InspectionSchedulingService inspections;
    private final AppUserService users;

    public InspectionAnnouncementService(InspectionPeriodRepository periods,
            InspectionAnnouncementRepository announcements, StaffAssignmentRepository staff,
            AttachmentRepository attachments, InspectionSchedulingService inspections, AppUserService users) {
        this.periods = periods;
        this.announcements = announcements;
        this.staff = staff;
        this.attachments = attachments;
        this.inspections = inspections;
        this.users = users;
    }

    /** Vorbefüllter Text und Empfänger für den Ankündigen-Dialog. */
    public DraftView draft(String userKey, Long id) {
        var period = requireForClerk(users.requireUser(userKey), id);
        var labels = period.getKind() != InspectionPeriodEntity.Kind.INDIVIDUAL ? null
            : inspections.flatLabelsByPeriod(List.of(period.getId())).getOrDefault(period.getId(), List.of());
        return new DraftView(period.getId(), period.getResidence().getName(), period.getKind(), flatCount(period),
            labels, channels(CHANNELS), announcementText(period));
    }

    @Transactional
    public AnnouncementView announce(String userKey, Long id, AnnounceRequest request) {
        var user = users.requireUser(userKey);
        var period = requireForClerk(user, id);
        InspectionPlanningService.requireVersion(period, request.version());
        if (period.getAnnouncedAt() != null) throw conflict("period_announced");
        if (period.getStatus() != Status.AGREED) throw conflict("invalid_period_status");
        if (!period.getStartsOn().isAfter(InspectionPlanningService.today())) throw badRequest("start_not_in_future");
        var message = requireMessage(request.message());
        var attachment = attachment(user, request.attachmentId());

        var now = Instant.now();
        period.setAnnouncedAt(now);
        period.setAnnouncedBy(user);
        periods.flush();
        return view(announcements.save(new InspectionAnnouncementEntity(period, Kind.ANNOUNCEMENT, user, now,
            flatCount(period), CHANNELS, message, attachment, attachmentName(request.attachmentName()))));
    }

    /** Neuer Termin nach der Ankündigung. Ohne erneute Abstimmung; alle WGs werden automatisch informiert. */
    @Transactional
    public AnnouncementView reschedule(String userKey, Long id, RescheduleRequest request) {
        var user = users.requireUser(userKey);
        var period = requireForClerk(user, id);
        InspectionPlanningService.requireVersion(period, request.version());
        if (period.getAnnouncedAt() == null) throw conflict("invalid_period_status");
        InspectionPlanningService.validateDates(request.startsOn(), request.endsOn());
        InspectionPlanningService.validateWindow(request.windowStart(), request.windowEnd());
        boolean changed = !request.startsOn().equals(period.getStartsOn())
            || !request.endsOn().equals(period.getEndsOn())
            || !Objects.equals(request.windowStart(), period.getWindowStart())
            || !Objects.equals(request.windowEnd(), period.getWindowEnd());
        if (!changed) throw badRequest("period_unchanged");

        var now = Instant.now();
        period.setStartsOn(request.startsOn());
        period.setEndsOn(request.endsOn());
        period.setWindowStart(request.windowStart());
        period.setWindowEnd(request.windowEnd());
        period.setRescheduledAt(now);
        periods.flush();
        return view(announcements.save(new InspectionAnnouncementEntity(period, Kind.RESCHEDULE, user, now,
            flatCount(period), CHANNELS, rescheduleText(period), null, null)));
    }

    public List<AnnouncementView> history(String userKey, Long id) {
        var period = requireForClerk(users.requireUser(userKey), id);
        return announcements.findByPeriodIdOrderBySentAtDescIdDesc(period.getId()).stream()
            .map(InspectionAnnouncementService::view).toList();
    }

    private InspectionPeriodEntity requireForClerk(UserEntity user, Long id) {
        return periods.findById(id)
            .filter(period -> staff.existsByResidenceIdAndUserIdAndKind(period.getResidence().getId(), user.getId(),
                StaffKind.CLERK))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private int flatCount(InspectionPeriodEntity period) {
        return inspections.flatCountByPeriod(List.of(period.getId())).getOrDefault(period.getId(), 0L).intValue();
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) throw badRequest("message_required");
        var text = message.strip();
        if (text.length() > MAX_MESSAGE_LENGTH) throw badRequest("message_too_long");
        return text;
    }

    /** Nur eigene, vorher hochgeladene Dateien dürfen angehängt werden. */
    private AttachmentEntity attachment(UserEntity user, Long attachmentId) {
        if (attachmentId == null) return null;
        return attachments.findById(attachmentId)
            .filter(attachment -> attachment.getUploadedBy().getId().equals(user.getId()))
            .orElseThrow(() -> badRequest("unknown_attachment"));
    }

    private static String attachmentName(String name) {
        if (name == null || name.isBlank()) return "Anhang";
        var stripped = name.strip();
        return stripped.length() > MAX_ATTACHMENT_NAME_LENGTH ? stripped.substring(0, MAX_ATTACHMENT_NAME_LENGTH) : stripped;
    }

    /** Vorlage je Kontrollart. Die Bewohnenden werden in der App geduzt. */
    static String announcementText(InspectionPeriodEntity period) {
        return period.getKind() == InspectionPeriodEntity.Kind.INDIVIDUAL ? individualText(period) : regularText(period);
    }

    private static String regularText(InspectionPeriodEntity period) {
        return "Liebe WG,\n\nin der Wohnanlage %s findet %s die reguläre Sauberkeitskontrolle statt.%s\n\n"
            .formatted(period.getResidence().getName(), range(period.getStartsOn(), period.getEndsOn()),
                window(period.getWindowStart(), period.getWindowEnd()))
            + checklistHint();
    }

    /** Den Anlass (z. B. Beschwerde) nennt die Vorlage bewusst nicht; die Sachbearbeitung kann ihn ergänzen. */
    private static String individualText(InspectionPeriodEntity period) {
        return "Liebe WG,\n\nfür eure WG in der Wohnanlage %s ist %s eine Sauberkeitskontrolle geplant.%s\n\n"
            .formatted(period.getResidence().getName(), range(period.getStartsOn(), period.getEndsOn()),
                window(period.getWindowStart(), period.getWindowEnd()))
            + checklistHint();
    }

    static String rescheduleText(InspectionPeriodEntity period) {
        return "Liebe WG,\n\nder Termin der Sauberkeitskontrolle in der Wohnanlage %s hat sich geändert. "
            .formatted(period.getResidence().getName())
            + "Die Kontrolle findet jetzt %s statt.%s\n\n"
                .formatted(range(period.getStartsOn(), period.getEndsOn()),
                    window(period.getWindowStart(), period.getWindowEnd()))
            + checklistHint();
    }

    private static String checklistHint() {
        return "Bitte bereitet eure WG anhand der Checkliste vor. Ihr findet sie in der App unter „Checkliste“.\n\n"
            + "Viele Grüße\nEure Sachbearbeitung";
    }

    private static String range(LocalDate from, LocalDate to) {
        return from.equals(to) ? "am " + DATE.format(from) : "vom %s bis %s".formatted(DATE.format(from), DATE.format(to));
    }

    private static String window(LocalTime start, LocalTime end) {
        return start == null || end == null ? ""
            : " Die Objektbetreuung kommt zwischen %s und %s Uhr.".formatted(TIME.format(start), TIME.format(end));
    }

    private static List<String> channels(String value) {
        return Arrays.asList(value.split(","));
    }

    private static AnnouncementView view(InspectionAnnouncementEntity entry) {
        var sender = entry.getSentBy();
        return new AnnouncementView(entry.getId(), entry.getKind(), entry.getSentAt(),
            new StaffView(sender.getId(), sender.getName()), entry.getResidence().getName(), entry.getFlatCount(),
            channels(entry.getChannels()), entry.getMessage(), entry.getAttachmentName(), entry.getStartsOn(),
            entry.getEndsOn(), entry.getWindowStart(), entry.getWindowEnd());
    }

    public record AnnounceRequest(String message, Long attachmentId, String attachmentName, Long version) {}

    public record RescheduleRequest(LocalDate startsOn, LocalDate endsOn, LocalTime windowStart, LocalTime windowEnd,
        Long version) {}

    /** {@code flatLabels} nur bei individuellen Kontrollen. */
    public record DraftView(Long periodId, String residenceName, InspectionPeriodEntity.Kind kind, int flatCount,
        List<String> flatLabels, List<String> channels, String message) {}

    public record AnnouncementView(Long id, Kind kind, Instant sentAt, StaffView sentBy, String residenceName,
        int flatCount, List<String> channels, String message, String attachmentName, LocalDate startsOn,
        LocalDate endsOn, LocalTime windowStart, LocalTime windowEnd) {}
}
