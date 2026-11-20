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
     * Die Sachbearbeitung sieht die ihr zugeordneten Wohnanlagen ({@link StaffKind#CLERK}),
     * die Objektbetreuung ihre Anlage aus dem Nutzerprofil.
     * WGs und Zuständigkeiten werden für die ganze Seite gebündelt geladen.
     */
    public Page<ResidenceView> list(String userKey, Pageable pageable) {
        var user = users.requireUser(userKey);
        var sorted = pageable.getSort().isSorted() ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name", "id"));
        Page<ResidenceEntity> page = user instanceof ClerkEntity
            ? residences.findAssigned(user.getId(), StaffKind.CLERK, sorted)
            : residences.findById(user.getResidence().getId(), sorted);
        var views = views(page.getContent());
        return page.map(residence -> views.get(residence.getId()));
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
