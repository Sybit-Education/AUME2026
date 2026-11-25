package de.seezeit.aume.overview;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionEntity;
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

/** Lesende Übersicht der Kontrollen je Wohnanlage für die Sachbearbeitung (US-47). */
@Service
@Transactional(readOnly = true)
public class InspectionOverviewService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final String FOLLOW_UP = "FOLLOW_UP";

    /** Bearbeitungsstand: aus dem anstehenden Zeitraum, angekündigt heißt {@code announcedAt} gesetzt. */
    public enum State { NONE, NO_UPCOMING, WAITING_FOR_CARETAKER, COUNTER_PROPOSED, AGREED, ANNOUNCED }

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
            .filter(row -> kind == null || (row.upcoming() != null && row.upcoming().kind().equals(kind.name())))
            .toList();
        return page(rows, pageable);
    }

    /** Zähler für offene Arbeit über alle Anlagen, unabhängig von den Filtern. */
    public Counts counts(String userKey) {
        var states = assigned(userKey).stream().map(residence -> row(residence).state()).toList();
        return new Counts(states.stream().filter(State.COUNTER_PROPOSED::equals).count(),
            states.stream().filter(State.AGREED::equals).count());
    }

    /** Kontrollen einer Anlage: ganze Zeiträume und Einzel-WG-Kontrollen ohne Zeitraum, neueste zuerst. */
    public Page<InspectionRow> inspections(String userKey, Long residenceId, Pageable pageable) {
        var residence = assigned(userKey).stream().filter(candidate -> candidate.getId().equals(residenceId))
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var periods = entityManager.createQuery(
                "select p from InspectionPeriodEntity p where p.residence = :residence", InspectionPeriodEntity.class)
            .setParameter("residence", residence).getResultStream()
            .map(p -> new InspectionRow(null, p.getKind().name(), p.getStartsOn(), p.getEndsOn(), stateOf(p).name()));
        var singles = entityManager.createQuery("""
                select i from InspectionEntity i join fetch i.flat f
                where f.residence = :residence and i.period is null
                """, InspectionEntity.class)
            .setParameter("residence", residence).getResultStream()
            .map(i -> {
                var day = i.getInspectedAt() != null ? i.getInspectedAt().atZone(ZONE).toLocalDate() : i.getDueOn();
                return new InspectionRow(i.getFlat().getLabel(), kindOf(i), day, day, i.getStatus().name());
            });
        var rows = Stream.concat(periods, singles)
            .sorted(Comparator.comparing(InspectionRow::startsOn, Comparator.nullsFirst(Comparator.reverseOrder())))
            .toList();
        return page(rows, pageable);
    }

    private List<ResidenceEntity> assigned(String userKey) {
        var user = users.requireUser(userKey);
        return residences.findAssigned(user.getId(), StaffKind.CLERK, Pageable.unpaged(Sort.by("name", "id")))
            .getContent();
    }

    private ResidenceRow row(ResidenceEntity residence) {
        var last = entityManager.createQuery("""
                select i from InspectionEntity i left join fetch i.period
                where i.flat.residence = :residence and i.inspectedAt is not null
                order by i.inspectedAt desc
                """, InspectionEntity.class)
            .setParameter("residence", residence).setMaxResults(1).getResultStream().findFirst()
            .map(i -> new LastInspection(i.getInspectedAt().atZone(ZONE).toLocalDate(), kindOf(i)))
            .orElse(null);
        var upcoming = entityManager.createQuery("""
                select p from InspectionPeriodEntity p
                where p.residence = :residence and (p.announcedAt is null or p.endsOn >= :today)
                order by p.startsOn, p.id
                """, InspectionPeriodEntity.class)
            .setParameter("residence", residence).setParameter("today", LocalDate.now(ZONE))
            .setMaxResults(1).getResultStream().findFirst().orElse(null);
        if (upcoming == null) {
            return new ResidenceRow(residence.getId(), residence.getName(), last, null,
                last == null ? State.NONE : State.NO_UPCOMING);
        }
        return new ResidenceRow(residence.getId(), residence.getName(), last,
            new Upcoming(upcoming.getId(), upcoming.getStartsOn(), upcoming.getEndsOn(), upcoming.getKind().name()), stateOf(upcoming));
    }

    private static State stateOf(InspectionPeriodEntity period) {
        if (period.getAnnouncedAt() != null) return State.ANNOUNCED;
        return State.valueOf(period.getStatus().name());
    }

    private static String kindOf(InspectionEntity inspection) {
        if (inspection.getRound() > 0) return FOLLOW_UP;
        return inspection.getPeriod() == null ? InspectionPeriodEntity.Kind.REGULAR.name() : inspection.getPeriod().getKind().name();
    }

    private static <T> Page<T> page(List<T> rows, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        return new PageImpl<>(rows.subList(from, Math.min(from + pageable.getPageSize(), rows.size())), pageable, rows.size());
    }

    public record ResidenceRow(Long id, String name, LastInspection lastInspection, Upcoming upcoming, State state) {}

    public record LastInspection(LocalDate on, String kind) {}

    public record Upcoming(Long id, LocalDate startsOn, LocalDate endsOn, String kind) {}

    /** Offene Arbeit; absichtlich keine Anzahl von Anlagen oder WGs. */
    public record Counts(long counterProposed, long agreedNotAnnounced) {}

    /** {@code flat} nur bei Einzel-WG-Kontrollen; {@code status} ist der Stand des Zeitraums oder der Kontrolle. */
    public record InspectionRow(String flat, String kind, LocalDate startsOn, LocalDate endsOn, String status) {}
}
