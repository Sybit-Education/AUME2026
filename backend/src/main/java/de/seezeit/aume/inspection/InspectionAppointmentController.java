package de.seezeit.aume.inspection;

import java.security.Principal;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionAppointmentController {
    private final InspectionAppointmentRepository appointments;

    public InspectionAppointmentController(InspectionAppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/api/student/inspection-appointment")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public ResponseEntity<AppointmentView> upcoming(Principal principal) {
        var candidates = appointments.findUpcoming(principal.getName(), LocalDate.now(),
            InspectionEntity.Status.PLANNED, PageRequest.ofSize(2));
        if (candidates.isEmpty()) return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();

        var inspection = candidates.getFirst();
        var period = inspection.getPeriod();
        if (candidates.size() == 2 && period.getStartsOn().equals(candidates.get(1).getPeriod().getStartsOn())) {
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new AppointmentView(inspection.getId(), period.getStartsOn(), period.getEndsOn()));
    }

    public record AppointmentView(Long id, LocalDate startsOn, LocalDate endsOn) {}
}
