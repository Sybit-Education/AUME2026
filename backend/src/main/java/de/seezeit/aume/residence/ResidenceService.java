package de.seezeit.aume.residence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.residence.ResidenceView.StaffView;
import de.seezeit.aume.user.CaretakerEntity;
import de.seezeit.aume.user.CaretakerRepository;
import de.seezeit.aume.user.ClerkEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ResidenceService {
    /** Stabile Fehlercodes für die Oberfläche (im Reason-Text der Antwort). */
    public static final String CARETAKER_REQUIRED = "caretaker_required";
    public static final String UNKNOWN_CARETAKER = "unknown_caretaker";
    public static final String DEPUTY_MUST_DIFFER = "deputy_must_differ";

    private final ResidenceRepository residences;
    private final FlatRepository flats;
    private final StaffAssignmentRepository staff;
    private final CaretakerRepository caretakers;
    private final AppUserService users;

    public ResidenceService(ResidenceRepository residences, FlatRepository flats,
                            StaffAssignmentRepository staff, CaretakerRepository caretakers,
                            AppUserService users) {
        this.residences = residences;
        this.flats = flats;
        this.staff = staff;
        this.caretakers = caretakers;
        this.users = users;
    }

    /**
     * Die Sachbearbeitung sieht die ihr zugeordneten Wohnanlagen ({@link StaffKind#CLERK}) mit WGs und
     * Zuständigkeit, gebündelt für die ganze Seite geladen. Die Objektbetreuung sieht die Anlagen, die sie
     * betreut oder vertritt (US-03), nur ID und Name. Alle anderen Sichten behalten das bisherige Verhalten:
     * ihre Anlage aus dem Nutzerprofil.
     */
    public Page<?> list(String userKey, Pageable pageable) {
        var user = users.requireUser(userKey);
        var sorted = pageable.getSort().isSorted() ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name", "id"));
        if (user instanceof CaretakerEntity) {
            return residences.findAssignedByKinds(user.getId(), StaffKind.CARETAKING, sorted)
                .map(residence -> new ResidenceSummary(residence.getId(), residence.getName()));
        }
        if (!(user instanceof ClerkEntity)) {
            return residences.findById(user.getResidence().getId(), pageable)
                .map(residence -> new ResidenceSummary(residence.getId(), residence.getName()));
        }
        var page = residences.findAssigned(user.getId(), StaffKind.CLERK, sorted);
        var views = views(page.getContent());
        return page.map(residence -> views.get(residence.getId()));
    }

    /** Bisherige Antwort für Sichten außer der Sachbearbeitung. */
    public record ResidenceSummary(Long id, String name) {}

    /** Anlagen, für die eine Objektbetreuung als Betreuung oder Vertretung eingetragen ist. */
    public Page<StaffResidenceView> listCaretakerAssignments(String userKey, Pageable pageable) {
        var user = users.requireUser(userKey);
        if (!(user instanceof CaretakerEntity)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        var sorted = pageable.getSort().isSorted() ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name", "id"));
        var page = residences.findAssignedByKinds(user.getId(), StaffKind.CARETAKING, sorted);
        var residenceIds = page.getContent().stream().map(ResidenceEntity::getId).toList();
        var flatLabels = flatLabelsByResidence(residenceIds);
        var kindsByResidence = new HashMap<Long, StaffKind>();
        if (!residenceIds.isEmpty()) {
            staff.findByResidenceIdInAndKindIn(residenceIds, StaffKind.CARETAKING).stream()
                .filter(assignment -> assignment.getUser().getId().equals(user.getId()))
                .forEach(assignment -> kindsByResidence.put(
                    assignment.getResidence().getId(), assignment.getKind()));
        }

        return page.map(residence -> {
            var kind = kindsByResidence.get(residence.getId());
            if (kind == null) {
                throw new IllegalStateException("Assigned residence is missing its caretaker assignment");
            }
            return new StaffResidenceView(residence.getId(), residence.getName(), kind,
                flatLabels.getOrDefault(residence.getId(), List.of()));
        });
    }

    /** Auswahlliste für Betreuung und Vertretung: nur Objektbetreuung, nur ID und Anzeigename. */
    public Page<StaffView> listCaretakers(Pageable pageable) {
        var sorted = pageable.getSort().isSorted() ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name", "id"));
        return caretakers.findAll(sorted).map(caretaker -> new StaffView(caretaker.getId(), caretaker.getName()));
    }

    /**
     * Ersetzt Betreuung und Vertretung einer Anlage. Beide müssen Objektbetreuung und verschiedene
     * Personen sein; ohne {@code deputyId} bleibt die Anlage ohne Vertretung. Eine Anlage, der die
     * Sachbearbeitung nicht zugeordnet ist, wird wie eine nicht vorhandene behandelt (404).
     */
    @Transactional
    public ResidenceView assignStaff(String userKey, Long residenceId, StaffAssignmentRequest request) {
        var user = users.requireUser(userKey);
        if (!staff.existsByResidenceIdAndUserIdAndKind(residenceId, user.getId(), StaffKind.CLERK)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var residence = residences.findById(residenceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.caretakerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CARETAKER_REQUIRED);
        }
        if (request.caretakerId().equals(request.deputyId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DEPUTY_MUST_DIFFER);
        }
        var caretaker = requireCaretaker(request.caretakerId());
        var deputy = request.deputyId() == null ? null : requireCaretaker(request.deputyId());

        // Erst löschen, dann neu anlegen: Bei einem Tausch würde ein Update die Eindeutigkeit
        // (Person, Anlage) vorübergehend verletzen. Die Zuordnung der Sachbearbeitung bleibt bestehen.
        staff.deleteAll(staff.findByResidenceIdInAndKindIn(List.of(residenceId), StaffKind.CARETAKING));
        staff.flush();
        staff.save(new StaffAssignmentEntity(caretaker, residence, StaffKind.CARETAKER));
        if (deputy != null) {
            staff.save(new StaffAssignmentEntity(deputy, residence, StaffKind.DEPUTY));
        }
        return views(List.of(residence)).get(residenceId);
    }

    private CaretakerEntity requireCaretaker(Long id) {
        return caretakers.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, UNKNOWN_CARETAKER));
    }

    private Map<Long, ResidenceView> views(List<ResidenceEntity> page) {
        var ids = page.stream().map(ResidenceEntity::getId).toList();
        var flatLabels = flatLabelsByResidence(ids);
        var staffByResidence = staffByResidence(ids);
        var result = new HashMap<Long, ResidenceView>();
        for (var residence : page) {
            var assigned = staffByResidence.getOrDefault(residence.getId(), Map.of());
            result.put(residence.getId(), new ResidenceView(residence.getId(), residence.getName(),
                flatLabels.getOrDefault(residence.getId(), List.of()),
                assigned.get(StaffKind.CARETAKER), assigned.get(StaffKind.DEPUTY)));
        }
        return result;
    }

    private Map<Long, List<String>> flatLabelsByResidence(Collection<Long> ids) {
        var result = new HashMap<Long, List<String>>();
        if (ids.isEmpty()) return result;
        flats.findByResidenceIdInOrderByLabel(ids).forEach(flat -> result
            .computeIfAbsent(flat.getResidence().getId(), id -> new ArrayList<>())
            .add(flat.getLabel()));
        return result;
    }

    private Map<Long, Map<StaffKind, StaffView>> staffByResidence(Collection<Long> ids) {
        var result = new HashMap<Long, Map<StaffKind, StaffView>>();
        if (ids.isEmpty()) return result;
        staff.findByResidenceIdInAndKindIn(ids, StaffKind.CARETAKING).forEach(assignment -> result
            .computeIfAbsent(assignment.getResidence().getId(), id -> new EnumMap<>(StaffKind.class))
            .put(assignment.getKind(), new StaffView(assignment.getUser().getId(), assignment.getUser().getName())));
        return result;
    }
}
