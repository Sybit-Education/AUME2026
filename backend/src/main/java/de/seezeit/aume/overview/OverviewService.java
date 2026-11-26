package de.seezeit.aume.overview;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.InspectionEntity.Status;
import de.seezeit.aume.residence.StaffKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Übersicht der Objektbetreuung (#41): Kennzahlen und anstehende Kontrollen der betreuten und vertretenen Wohnanlagen.
 * Nur lesend; Aktionen wie "Kontrolle starten" oder "Beantworten" liegen bei den jeweiligen Features.
 */
@Service
@Transactional(readOnly = true)
public class OverviewService {
    static final int UPCOMING_LIMIT = 10;
    private static final Set<Status> OPEN = Set.of(Status.PLANNED, Status.DRAFT);
    private static final Comparator<UpcomingInspection> BY_DATE = Comparator
        .comparing(UpcomingInspection::date, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(UpcomingInspection::id);

    /**
     * Zustand aus Sicht der Objektbetreuung. Vor der Ankündigung folgt er der Terminabstimmung (US-17 #35):
     * {@code PROPOSED} wartet auf die Objektbetreuung, {@code COUNTER_PROPOSED} auf die Sachbearbeitung,
     * {@code AGREED} auf die Ankündigung. {@code PLANNED}: Erstkontrolle ohne Zeitraum.
     */
    public enum State { PROPOSED, COUNTER_PROPOSED, AGREED, ANNOUNCED, PLANNED, IN_PROGRESS }

    public enum Kind { INITIAL, FOLLOW_UP }

    private final OverviewRepository inspections;
    private final AppUserService users;

    public OverviewService(OverviewRepository inspections, AppUserService users) {
        this.inspections = inspections;
        this.users = users;
    }

    public CaretakerOverview caretakerOverview(String userKey) {
        var user = users.requireUser(userKey);
        // Alle Anlagen, die die Person betreut oder vertritt (US-02 #30, Vertretung #45).
        var open = inspections.findByAssignedStaff(user.getId(), StaffKind.CARETAKING, OPEN).stream()
            .map(inspection -> toUpcoming(inspection, user.getId()))
            .sorted(BY_DATE)
            .toList();
        return new CaretakerOverview(
            // Beantwortet wird je Kontrollzeitraum, nicht je WG.
            (int) open.stream().filter(UpcomingInspection::canRespond).map(UpcomingInspection::periodId)
                .distinct().count(),
            count(open, inspection -> inspection.kind() == Kind.INITIAL && inspection.state() == State.ANNOUNCED),
            count(open, inspection -> inspection.kind() == Kind.FOLLOW_UP),
            // Schnittstelle Schadensmeldungen (US-14, #12): noch kein Datenmodell. null bedeutet
            // "nicht verfügbar" und wird im Frontend so angezeigt, nicht als 0.
            null,
            open.stream().limit(UPCOMING_LIMIT).toList());
    }

    private static UpcomingInspection toUpcoming(InspectionEntity inspection, Long userId) {
        var flat = inspection.getFlat();
        var period = inspection.getPeriod();
        var kind = inspection.getRound() > 0 ? Kind.FOLLOW_UP : Kind.INITIAL;
        var state = state(inspection);
        // Antworten darf nur die im Vorschlag eingetragene Person (InspectionPlanningService#agree), keine Vertretung.
        var canRespond = state == State.PROPOSED && period.getCaretaker() != null
            && period.getCaretaker().getId().equals(userId);
        return new UpcomingInspection(inspection.getId(), flat.getLabel(), flat.getResidence().getName(), kind,
            inspection.getRound(), state, period == null ? null : period.getId(), canRespond,
            period == null ? null : period.getStartsOn(), period == null ? null : period.getEndsOn(),
            inspection.getDueOn());
    }

    private static State state(InspectionEntity inspection) {
        if (inspection.getStatus() == Status.DRAFT) return State.IN_PROGRESS;
        var period = inspection.getPeriod();
        if (period != null) {
            if (period.getAnnouncedAt() != null) return State.ANNOUNCED;
            return switch (period.getStatus()) {
                case WAITING_FOR_CARETAKER -> State.PROPOSED;
                case COUNTER_PROPOSED -> State.COUNTER_PROPOSED;
                case AGREED -> State.AGREED;
            };
        }
        // Nachkontrollen haben keinen Zeitraum, ihre Frist (dueOn) steht mit dem Ergebnis fest.
        return inspection.getRound() > 0 ? State.ANNOUNCED : State.PLANNED;
    }

    private static int count(List<UpcomingInspection> inspections, Predicate<UpcomingInspection> filter) {
        return (int) inspections.stream().filter(filter).count();
    }

    /** {@code openDamages} ist {@code null}, solange es keine Schadensmeldungen gibt (#12). */
    public record CaretakerOverview(int proposals, int announced, int followUps, Integer openDamages,
        List<UpcomingInspection> upcoming) {}

    /**
     * Zeitraum ({@code startsOn}–{@code endsOn}) bei Erstkontrollen, Frist ({@code dueOn}) bei Nachkontrollen.
     * {@code canRespond}: Die Person kann den Vorschlag {@code periodId} unter {@code /api/inspection-periods} beantworten.
     */
    public record UpcomingInspection(Long id, String flatLabel, String residenceName, Kind kind, int round,
            State state, Long periodId, boolean canRespond, LocalDate startsOn, LocalDate endsOn, LocalDate dueOn) {
        LocalDate date() {
            return startsOn != null ? startsOn : dueOn;
        }
    }
}
