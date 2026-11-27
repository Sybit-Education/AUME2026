package de.seezeit.aume.residence;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schnittstelle des Moduls {@code residence} für andere Module: Für welche Wohnanlagen ist eine
 * Objektbetreuung zuständig (US-03)? Zuständig ist, wer in {@code staff_assignments} als Betreuung
 * ({@code CARETAKER}) oder Vertretung ({@code DEPUTY}) eingetragen ist; die Vertretung ist fest und
 * hat dieselben Rechte wie die Betreuung.
 */
@Service
@Transactional(readOnly = true)
public class StaffAccessService {
    private final StaffAssignmentRepository staff;

    StaffAccessService(StaffAssignmentRepository staff) {
        this.staff = staff;
    }

    /** IDs der Wohnanlagen, die der Nutzer betreut oder vertritt; leer, wenn er für keine zuständig ist. */
    public Set<Long> residencesInCare(Long userId) {
        return staff.findByUserIdAndKindIn(userId, StaffKind.CARETAKING).stream()
            .map(assignment -> assignment.getResidence().getId())
            .collect(Collectors.toUnmodifiableSet());
    }
}
