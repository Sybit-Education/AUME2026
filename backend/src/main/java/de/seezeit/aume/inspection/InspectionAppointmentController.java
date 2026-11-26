package de.seezeit.aume.inspection;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionAppointmentController {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final InspectionAppointmentRepository appointments;

    public InspectionAppointmentController(InspectionAppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/api/student/inspection-appointment")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public ResponseEntity<AppointmentView> upcoming(Principal principal) {
        var today = LocalDate.now(BERLIN);
        var candidates = appointments.findUpcoming(principal.getName(), today,
            InspectionEntity.Status.PLANNED, PageRequest.ofSize(2));
        if (candidates.isEmpty()) return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();

        var inspection = candidates.getFirst();
        var period = inspection.getPeriod();
        if (candidates.size() == 2 && period.getStartsOn().equals(candidates.get(1).getPeriod().getStartsOn())) {
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        }
        // findUpcoming returns only announced periods.
        var appointmentChanged = period.getRescheduledAt() != null && today.isBefore(period.getStartsOn());
        var showInitialInspection = inspection.getRound() == 0 && period.getRescheduledAt() == null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new AppointmentView(inspection.getId(), period.getStartsOn(), period.getEndsOn(),
                appointmentChanged, showInitialInspection));
    }

    public record AppointmentView(Long id, LocalDate startsOn, LocalDate endsOn, boolean appointmentChanged,
                                  boolean showInitialInspection) {}
}
