package de.seezeit.aume.inspection;

import de.seezeit.aume.inspection.InspectionEntity.Status;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentAppointmentDetailsService {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final PageRequest CHECK_TIE = PageRequest.ofSize(2);
    private static final List<Status> OPEN = List.of(Status.PLANNED, Status.DRAFT, Status.FINALIZED);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final StudentMembershipRepository memberships;
    private final InspectionAppointmentRepository appointments;
    private final InspectionResultService results;

    StudentAppointmentDetailsService(StudentMembershipRepository memberships,
            InspectionAppointmentRepository appointments, InspectionResultService results) {
        this.memberships = memberships;
        this.appointments = appointments;
        this.results = results;
    }

    @Transactional(readOnly = true)
    public AppointmentDetails find(String userKey, LocalDate today) {
        var active = memberships.findActive(userKey, today);
        var flatIds = active.stream().map(membership -> membership.getFlat().getId()).distinct().toList();
        if (flatIds.size() != 1) return null;

        var flatId = flatIds.getFirst();
        var candidates = appointments.findOpenDetails(flatId, today, OPEN, CHECK_TIE);
        if (candidates.isEmpty()) candidates = appointments.findLatestDetails(flatId, CHECK_TIE);
        if (candidates.isEmpty()) return null;

        var inspection = candidates.getFirst();
        var period = inspection.getPeriod();
        if (candidates.size() > 1 && period.getStartsOn().equals(candidates.get(1).getPeriod().getStartsOn())) {
            return null;
        }
        boolean past = period.getEndsOn().isBefore(today);
        return switch (inspection.getStatus()) {
            case PLANNED -> past ? null : view(inspection, today, ResidentStatus.ANNOUNCED, Display.UPCOMING);
            case DRAFT -> past ? view(inspection, today, null, Display.PAST)
                : view(inspection, today, ResidentStatus.ANNOUNCED, Display.CURRENT);
            case FINALIZED -> null;
            case PUBLISHED -> {
                var movedIn = active.getFirst().getValidFrom().atStartOfDay(BERLIN).toInstant();
                yield results.hasVisibleResult(inspection.getId(), flatId, movedIn)
                    ? view(inspection, today, ResidentStatus.RESULT_AVAILABLE, Display.PAST) : null;
            }
        };
    }

    private static AppointmentDetails view(InspectionEntity inspection, LocalDate today,
            ResidentStatus status, Display display) {
        var period = inspection.getPeriod();
        var rescheduled = period.getRescheduledAt() != null;
        return new AppointmentDetails(inspection.getId(), period.getStartsOn(), period.getEndsOn(),
            rescheduled && today.isBefore(period.getStartsOn()), inspection.getRound() == 0 && !rescheduled,
            time(period.getWindowStart()), time(period.getWindowEnd()), status, display);
    }

    private static String time(LocalTime value) {
        return value == null ? null : TIME.format(value);
    }

    public enum ResidentStatus { ANNOUNCED, RESULT_AVAILABLE }
    public enum Display { UPCOMING, CURRENT, PAST }

    public record AppointmentDetails(Long id, LocalDate startsOn, LocalDate endsOn,
            boolean appointmentChanged, boolean showInitialInspection, String windowStart, String windowEnd,
            ResidentStatus status, Display display) {}
}
