package de.seezeit.aume.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionSchedulingService;
import de.seezeit.aume.planning.InspectionPeriodEntity.Kind;
import de.seezeit.aume.planning.InspectionPeriodEntity.Status;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.ResidenceService;
import de.seezeit.aume.residence.ResidenceView.StaffView;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.residence.StaffKind;
import de.seezeit.aume.user.CaretakerEntity;
import de.seezeit.aume.user.CaretakerRepository;
import de.seezeit.aume.user.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Kontrollzeitraum einer Wohnanlage planen und mit der Objektbetreuung abstimmen (US-17). */
@Service
@Transactional(readOnly = true)
public class InspectionPlanningService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final int MAX_REASON_LENGTH = 2000;

    private final InspectionPeriodRepository periods;
    private final ResidenceRepository residences;
    private final FlatRepository flats;
    private final StaffAssignmentRepository staff;
    private final CaretakerRepository caretakers;
    private final InspectionSchedulingService inspections;
    private final AppUserService users;

    public InspectionPlanningService(InspectionPeriodRepository periods, ResidenceRepository residences,
            FlatRepository flats, StaffAssignmentRepository staff, CaretakerRepository caretakers,
            InspectionSchedulingService inspections, AppUserService users) {
        this.periods = periods;
        this.residences = residences;
        this.flats = flats;
        this.staff = staff;
        this.caretakers = caretakers;
        this.inspections = inspections;
        this.users = users;
    }

    public OptionsView options(String userKey) {
        var assigned = assignedResidences(users.requireUser(userKey));
        var ids = assigned.stream().map(ResidenceEntity::getId).toList();
        var flatsByResidence = ids.isEmpty() ? Map.<Long, List<FlatEntity>>of()
            : flats.findByResidenceIdInOrderByLabel(ids).stream()
                .collect(Collectors.groupingBy(flat -> flat.getResidence().getId()));
        var open = inspections.flatsWithOpenInspection(
            flatsByResidence.values().stream().flatMap(List::stream).map(FlatEntity::getId).toList());
        var caretakerByResidence = ids.isEmpty() ? Map.<Long, Long>of()
            : staff.findByResidenceIdInAndKindIn(ids, Set.of(StaffKind.CARETAKER)).stream()
                .collect(Collectors.toMap(a -> a.getResidence().getId(), a -> a.getUser().getId(), (a, b) -> a));
        var options = assigned.stream().map(residence -> {
            var residenceFlats = flatsByResidence.getOrDefault(residence.getId(), List.of());
            return new ResidenceOption(residence.getId(), residence.getName(), residenceFlats.size(),
                caretakerByResidence.get(residence.getId()),
                residenceFlats.stream().filter(flat -> open.contains(flat.getId())).map(FlatEntity::getLabel).toList());
        }).toList();
        var people = caretakers.findAll(Sort.by("name", "id")).stream()
            .map(person -> new StaffView(person.getId(), person.getName())).toList();
        return new OptionsView(options, people);
    }

    public Page<PeriodView> list(String userKey, Pageable pageable) {
        var ids = assignedResidences(users.requireUser(userKey)).stream().map(ResidenceEntity::getId).toList();
        if (ids.isEmpty()) return Page.empty(pageable);
        return views(periods.findPlanned(ids, today(), pageable));
    }

    public Page<PeriodView> assigned(String userKey, Pageable pageable) {
        return views(periods.findAssigned(users.requireUser(userKey).getId(), pageable));
    }

    @Transactional
    public PeriodView create(String userKey, CreatePeriodRequest request) {
        var user = users.requireUser(userKey);
        var residence = requireAssignedResidence(user, request.residenceId());
        validateDates(request.startsOn(), request.endsOn());
        validateWindow(request.windowStart(), request.windowEnd());
        var caretaker = requireCaretaker(request.caretakerId());

        var residenceFlats = flats.findByResidenceIdInOrderByLabel(List.of(residence.getId()));
        var open = inspections.flatsWithOpenInspection(residenceFlats.stream().map(FlatEntity::getId).toList());
        var free = residenceFlats.stream().filter(flat -> !open.contains(flat.getId())).toList();
        if (free.isEmpty()) throw conflict("no_flats_available");

        var period = new InspectionPeriodEntity(residence, request.startsOn(), request.endsOn(), user);
        period.setWindowStart(request.windowStart());
        period.setWindowEnd(request.windowEnd());
        period.setCaretaker(caretaker);
        periods.saveAndFlush(period);
        inspections.schedule(free, period);
        return view(period, free.size());
    }

    /** Neuer Zeitraum: Jede Änderung, auch an der Person, muss die Objektbetreuung erneut bestätigen. */
    @Transactional
    public PeriodView update(String userKey, Long id, UpdatePeriodRequest request) {
        var period = requireForClerk(users.requireUser(userKey), id);
        requireVersion(period, request.version());
        if (period.getAnnouncedAt() != null) throw conflict("period_announced");
        validateDates(request.startsOn(), request.endsOn());
        validateWindow(request.windowStart(), request.windowEnd());
        var caretaker = requireCaretaker(request.caretakerId());

        boolean changed = !request.startsOn().equals(period.getStartsOn())
            || !request.endsOn().equals(period.getEndsOn())
            || !Objects.equals(request.windowStart(), period.getWindowStart())
            || !Objects.equals(request.windowEnd(), period.getWindowEnd())
            || period.getCaretaker() == null || !caretaker.getId().equals(period.getCaretaker().getId());
        if (changed || period.getStatus() != Status.AGREED) {
            period.setStartsOn(request.startsOn());
            period.setEndsOn(request.endsOn());
            period.setWindowStart(request.windowStart());
            period.setWindowEnd(request.windowEnd());
            period.setCaretaker(caretaker);
            period.setStatus(Status.WAITING_FOR_CARETAKER);
            clearCounter(period);
        }
        return saved(period);
    }

    @Transactional
    public PeriodView acceptCounter(String userKey, Long id, Long version) {
        var period = requireForClerk(users.requireUser(userKey), id);
        requireVersion(period, version);
        if (period.getAnnouncedAt() != null) throw conflict("period_announced");
        if (period.getStatus() != Status.COUNTER_PROPOSED) throw conflict("invalid_period_status");
        validateDates(period.getCounterStartsOn(), period.getCounterEndsOn());
        period.setStartsOn(period.getCounterStartsOn());
        period.setEndsOn(period.getCounterEndsOn());
        period.setWindowStart(period.getCounterWindowStart());
        period.setWindowEnd(period.getCounterWindowEnd());
        period.setStatus(Status.AGREED);
        clearCounter(period);
        return saved(period);
    }

    @Transactional
    public PeriodView agree(String userKey, Long id, Long version) {
        var period = requireWaitingForCaretaker(users.requireUser(userKey), id, version);
        validateDates(period.getStartsOn(), period.getEndsOn());
        period.setStatus(Status.AGREED);
        return saved(period);
    }

    @Transactional
    public PeriodView counter(String userKey, Long id, CounterRequest request) {
        var period = requireWaitingForCaretaker(users.requireUser(userKey), id, request.version());
        validateDates(request.startsOn(), request.endsOn());
        validateWindow(request.windowStart(), request.windowEnd());
        if (request.reason() == null || request.reason().isBlank()) throw badRequest("reason_required");
        var reason = request.reason().strip();
        if (reason.length() > MAX_REASON_LENGTH) throw badRequest("reason_too_long");
        // Ohne eigene Uhrzeit gilt weiter die Uhrzeit aus dem Vorschlag.
        boolean ownWindow = request.windowStart() != null;
        period.setCounterStartsOn(request.startsOn());
        period.setCounterEndsOn(request.endsOn());
        period.setCounterWindowStart(ownWindow ? request.windowStart() : period.getWindowStart());
        period.setCounterWindowEnd(ownWindow ? request.windowEnd() : period.getWindowEnd());
        period.setCounterReason(reason);
        period.setStatus(Status.COUNTER_PROPOSED);
        return saved(period);
    }

    /** Zieht einen noch nicht angekündigten Vorschlag zurück und gibt die WGs wieder frei. */
    @Transactional
    public void withdraw(String userKey, Long id, Long version) {
        var period = requireForClerk(users.requireUser(userKey), id);
        requireVersion(period, version);
        if (period.getAnnouncedAt() != null) throw conflict("period_announced");
        if (!inspections.removeIfNotStarted(period)) throw conflict("period_started");
        periods.delete(period);
        periods.flush();
    }

    private InspectionPeriodEntity requireWaitingForCaretaker(UserEntity user, Long id, Long version) {
        var period = periods.findById(id)
            .filter(candidate -> candidate.getCaretaker() != null
                && candidate.getCaretaker().getId().equals(user.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireVersion(period, version);
        if (period.getAnnouncedAt() != null) throw conflict("period_announced");
        if (period.getStatus() != Status.WAITING_FOR_CARETAKER) throw conflict("invalid_period_status");
        return period;
    }

    private InspectionPeriodEntity requireForClerk(UserEntity user, Long id) {
        return periods.findById(id)
            .filter(period -> isAssigned(user, period.getResidence().getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ResidenceEntity requireAssignedResidence(UserEntity user, Long residenceId) {
        if (residenceId == null || !isAssigned(user, residenceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return residences.findById(residenceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private boolean isAssigned(UserEntity user, Long residenceId) {
        return staff.existsByResidenceIdAndUserIdAndKind(residenceId, user.getId(), StaffKind.CLERK);
    }

    private List<ResidenceEntity> assignedResidences(UserEntity user) {
        return residences.findAssigned(user.getId(), StaffKind.CLERK, Pageable.unpaged(Sort.by("name", "id")))
            .getContent();
    }

    private CaretakerEntity requireCaretaker(Long id) {
        if (id == null) throw badRequest(ResidenceService.CARETAKER_REQUIRED);
        return caretakers.findById(id).orElseThrow(() -> badRequest(ResidenceService.UNKNOWN_CARETAKER));
    }

    static void requireVersion(InspectionPeriodEntity period, Long version) {
        if (version == null || version != period.getVersion()) throw conflict("period_changed");
    }

    static void validateDates(LocalDate startsOn, LocalDate endsOn) {
        if (startsOn == null || endsOn == null) throw badRequest("dates_required");
        if (endsOn.isBefore(startsOn)) throw badRequest("end_before_start");
        if (!startsOn.isAfter(today())) throw badRequest("start_not_in_future");
    }

    static void validateWindow(LocalTime start, LocalTime end) {
        if (start == null && end == null) return;
        if (start == null || end == null || !end.isAfter(start)) throw badRequest("invalid_time_window");
    }

    private static void clearCounter(InspectionPeriodEntity period) {
        period.setCounterStartsOn(null);
        period.setCounterEndsOn(null);
        period.setCounterWindowStart(null);
        period.setCounterWindowEnd(null);
        period.setCounterReason(null);
    }

    private PeriodView saved(InspectionPeriodEntity period) {
        periods.flush();
        var count = inspections.flatCountByPeriod(List.of(period.getId())).getOrDefault(period.getId(), 0L);
        return view(period, count);
    }

    private Page<PeriodView> views(Page<InspectionPeriodEntity> page) {
        var ids = new ArrayList<Long>();
        page.forEach(period -> ids.add(period.getId()));
        var counts = inspections.flatCountByPeriod(ids);
        return page.map(period -> view(period, counts.getOrDefault(period.getId(), 0L)));
    }

    private static PeriodView view(InspectionPeriodEntity period, long flatCount) {
        var caretaker = period.getCaretaker();
        var counter = period.getStatus() != Status.COUNTER_PROPOSED ? null
            : new CounterView(period.getCounterStartsOn(), period.getCounterEndsOn(),
                period.getCounterWindowStart(), period.getCounterWindowEnd(), period.getCounterReason());
        return new PeriodView(period.getId(), period.getResidence().getId(), period.getResidence().getName(),
            period.getKind(), period.getStatus(), period.getAnnouncedAt(), period.getRescheduledAt(), period.getStartsOn(), period.getEndsOn(),
            period.getWindowStart(), period.getWindowEnd(),
            caretaker == null ? null : new StaffView(caretaker.getId(), caretaker.getName()),
            flatCount, counter, period.getVersion());
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    static ResponseStatusException badRequest(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }

    static ResponseStatusException conflict(String code) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code);
    }

    public record CreatePeriodRequest(Long residenceId, LocalDate startsOn, LocalDate endsOn,
        LocalTime windowStart, LocalTime windowEnd, Long caretakerId) {}

    public record UpdatePeriodRequest(LocalDate startsOn, LocalDate endsOn, LocalTime windowStart,
        LocalTime windowEnd, Long caretakerId, Long version) {}

    public record CounterRequest(LocalDate startsOn, LocalDate endsOn, LocalTime windowStart, LocalTime windowEnd,
        String reason, Long version) {}

    public record VersionRequest(Long version) {}

    public record OptionsView(List<ResidenceOption> residences, List<StaffView> caretakers) {}

    public record ResidenceOption(Long id, String name, int flatCount, Long caretakerId,
        List<String> flatsWithOpenInspection) {}

    public record CounterView(LocalDate startsOn, LocalDate endsOn, LocalTime windowStart, LocalTime windowEnd,
        String reason) {}

    public record PeriodView(Long id, Long residenceId, String residenceName, Kind kind, Status status,
        Instant announcedAt, Instant rescheduledAt, LocalDate startsOn, LocalDate endsOn, LocalTime windowStart, LocalTime windowEnd,
        StaffView caretaker, long flatCount, CounterView counter, long version) {}
}
