package de.seezeit.aume.overview;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.InspectionEntity.Result;
import de.seezeit.aume.inspection.InspectionEntity.Status;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Lesende Übersicht der Kontrollrunden je Wohnanlage für die Sachbearbeitung (US-47). */
@Service
@Transactional(readOnly = true)
public class InspectionOverviewService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final String FOLLOW_UP = "FOLLOW_UP";
    private static final Set<Status> DONE = EnumSet.of(Status.FINALIZED, Status.PUBLISHED);

    /**
     * Stand einer Runde bzw. der Zeile. {@code NONE}/{@code NO_UPCOMING} nur auf der Hauptliste,
     * wenn es keine laufende oder nächste Runde gibt.
     */
    public enum State {
        NONE, NO_UPCOMING, WAITING_FOR_CARETAKER, COUNTER_PROPOSED, AGREED, ANNOUNCED, RUNNING, COMPLETED
    }

    @PersistenceContext
    private EntityManager entityManager;

    private final AppUserService users;
    private final ResidenceRepository residences;

    public InspectionOverviewService(AppUserService users, ResidenceRepository residences) {
        this.users = users;
        this.residences = residences;
    }

    public Page<ResidenceRow> overview(String userKey, State state, InspectionPeriodEntity.Kind kind, Pageable pageable) {
        var rows = assigned(userKey).stream().map(this::row)
            .filter(row -> state == null || row.state() == state)
            .filter(row -> kind == null || (row.current() != null && row.current().kind().equals(kind.name())))
            .toList();
        return page(rows, pageable);
    }

    /** Zähler für offene Arbeit über alle Anlagen, unabhängig von den Filtern. */
    public Counts counts(String userKey) {
        var states = assigned(userKey).stream().map(residence -> row(residence).state()).toList();
        return new Counts(states.stream().filter(State.COUNTER_PROPOSED::equals).count(),
            states.stream().filter(State.AGREED::equals).count());
    }

    /** Runden der Anlage und darunter nur noch offene Einzel-WG-Kontrollen. */
    public ResidenceDetail detail(String userKey, Long residenceId) {
        var residence = requireAssigned(userKey, residenceId);
        var rounds = periodsOf(residence).stream()
            .map(this::roundOf)
            .sorted(Comparator.comparing(RoundView::startsOn).reversed().thenComparing(RoundView::id, Comparator.reverseOrder()))
            .toList();
        // Nachkontrollen (round > 0), individuelle Kontrollen (US-18b) und Einzelkontrollen ohne Runde; geplante
        // Erstkontrollen ohne Zeitraum gehören nicht hierher und bleiben den anderen Features unangetastet.
        var open = entityManager.createQuery("""
                select i from InspectionEntity i join fetch i.flat f left join fetch i.period p
                where f.residence = :residence and i.status not in :done
                  and (i.round > 0 or p.kind = :individual
                    or (p is null and i.round = 0 and i.status <> :planned))
                order by i.id
                """, InspectionEntity.class)
            .setParameter("residence", residence).setParameter("done", DONE)
            .setParameter("planned", Status.PLANNED).setParameter("individual", InspectionPeriodEntity.Kind.INDIVIDUAL)
            .getResultStream().map(this::openOf).toList();
        return new ResidenceDetail(residence.getId(), residence.getName(), rounds, open);
    }

    private ResidenceRow row(ResidenceEntity residence) {
        var rounds = periodsOf(residence).stream().map(this::roundOf).toList();
        var last = rounds.stream().filter(round -> round.state() == State.COMPLETED)
            .max(Comparator.comparing(RoundView::endsOn).thenComparing(RoundView::id)).orElse(null);
        var current = rounds.stream().filter(round -> round.state() != State.COMPLETED)
            .min(Comparator.comparing(RoundView::startsOn).thenComparing(RoundView::id)).orElse(null);
        if (current == null) {
            return new ResidenceRow(residence.getId(), residence.getName(), last, null,
                last == null ? State.NONE : State.NO_UPCOMING);
        }
        return new ResidenceRow(residence.getId(), residence.getName(), last, current, current.state());
    }

    private RoundView roundOf(InspectionPeriodEntity period) {
        var inspections = entityManager.createQuery(
                "select i from InspectionEntity i where i.period = :period", InspectionEntity.class)
            .setParameter("period", period).getResultList();
        int total = inspections.size();
        int controlled = (int) inspections.stream().filter(i -> DONE.contains(i.getStatus())).count();
        int passed = (int) inspections.stream().filter(i -> i.getOverallResult() == Result.PASSED).count();
        int failed = (int) inspections.stream().filter(i -> i.getOverallResult() == Result.FAILED).count();
        return new RoundView(period.getId(), period.getStartsOn(), period.getEndsOn(), period.getKind().name(),
            stateOf(period, controlled, total), controlled, total, passed, failed);
    }

    private static State stateOf(InspectionPeriodEntity period, int controlled, int total) {
        if (period.getAnnouncedAt() == null) return State.valueOf(period.getStatus().name());
        if (total > 0 && controlled >= total) return State.COMPLETED;
        if (controlled > 0) return State.RUNNING;
        return State.ANNOUNCED;
    }

    private OpenInspection openOf(InspectionEntity inspection) {
        var day = inspection.getDueOn() != null ? inspection.getDueOn()
            : inspection.getInspectedAt() != null ? inspection.getInspectedAt().atZone(ZONE).toLocalDate() : null;
        var starts = inspection.getPeriod() != null ? inspection.getPeriod().getStartsOn() : day;
        var ends = inspection.getPeriod() != null ? inspection.getPeriod().getEndsOn() : day;
        return new OpenInspection(inspection.getFlat().getLabel(), kindOf(inspection), starts, ends, "OPEN");
    }

    private static String kindOf(InspectionEntity inspection) {
        if (inspection.getRound() > 0) return FOLLOW_UP;
        return inspection.getPeriod() == null ? InspectionPeriodEntity.Kind.REGULAR.name()
            : inspection.getPeriod().getKind().name();
    }

    /** Nur Runden der ganzen Anlage; individuelle Kontrollen stehen bei den offenen Einzel-WG-Kontrollen (AK 2). */
    private List<InspectionPeriodEntity> periodsOf(ResidenceEntity residence) {
        return entityManager.createQuery(
                "select p from InspectionPeriodEntity p where p.residence = :residence and p.kind = :regular",
                InspectionPeriodEntity.class)
            .setParameter("residence", residence).setParameter("regular", InspectionPeriodEntity.Kind.REGULAR)
            .getResultList();
    }

    private List<ResidenceEntity> assigned(String userKey) {
        var user = users.requireUser(userKey);
        return residences.findAssigned(user.getId(), StaffKind.CLERK, Pageable.unpaged(Sort.by("name", "id")))
            .getContent();
    }

    private ResidenceEntity requireAssigned(String userKey, Long residenceId) {
        return assigned(userKey).stream().filter(candidate -> candidate.getId().equals(residenceId)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static <T> Page<T> page(List<T> rows, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        return new PageImpl<>(rows.subList(from, Math.min(from + pageable.getPageSize(), rows.size())), pageable, rows.size());
    }

    public record ResidenceRow(Long id, String name, RoundView lastCompleted, RoundView current, State state) {}

    public record RoundView(Long id, LocalDate startsOn, LocalDate endsOn, String kind, State state,
            int controlled, int total, int passed, int failed) {}

    public record Counts(long counterProposed, long agreedNotAnnounced) {}

    public record ResidenceDetail(Long id, String name, List<RoundView> rounds, List<OpenInspection> openInspections) {}

    /** Offene Einzel-WG-Kontrolle; {@code outcome} ist immer {@code OPEN}, bis die Kontrolle abgeschlossen ist. */
    public record OpenInspection(String flat, String kind, LocalDate startsOn, LocalDate endsOn, String outcome) {}
}
