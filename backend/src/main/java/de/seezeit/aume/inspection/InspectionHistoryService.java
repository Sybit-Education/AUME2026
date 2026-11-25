package de.seezeit.aume.inspection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.FindingEntity.Rating;
import de.seezeit.aume.inspection.InspectionEntity.Result;
import de.seezeit.aume.inspection.InspectionEntity.Status;
import jakarta.persistence.criteria.Predicate;

/**
 * Kontroll-Historie der Objektbetreuung: abgeschlossene und veröffentlichte Kontrollen der eigenen
 * Wohnanlage, neueste zuerst. Die Detailansicht nutzt {@link InspectionService#view}.
 */
@Service
@Transactional(readOnly = true)
public class InspectionHistoryService {
    static final Set<Status> DONE = Set.of(Status.FINALIZED, Status.PUBLISHED);
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final int MAX_FLAT_FILTER = 80;

    private final InspectionRepository inspections;
    private final FindingRepository findings;
    private final AppUserService users;

    public InspectionHistoryService(InspectionRepository inspections, FindingRepository findings,
            AppUserService users) {
        this.inspections = inspections;
        this.findings = findings;
        this.users = users;
    }

    public Page<HistoryEntry> history(String userKey, HistoryFilter filter, HistoryOrder order, Pageable pageable) {
        var user = users.requireUser(userKey);
        validate(filter);
        // Nur die Spalten aus HistoryOrder sind sortierbar; ein `sort`-Parameter des Clients wird ignoriert.
        var page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order.toSort());
        var result = inspections.findAll(matching(user.getResidence().getId(), filter), page);

        var ids = result.getContent().stream().map(InspectionEntity::getId).toList();
        var deficiencies = ids.isEmpty() ? Map.<Long, Long>of()
            : findings.findByInspectionIdIn(ids).stream()
                .filter(finding -> finding.getRating() == Rating.DEFICIENT)
                .collect(Collectors.groupingBy(finding -> finding.getInspection().getId(), Collectors.counting()));

        return result.map(inspection -> new HistoryEntry(inspection.getId(), inspection.getFlat().getLabel(),
            inspection.getInspectedAt(), inspection.getRound(), inspection.getStatus(),
            inspection.getOverallResult(), deficiencies.getOrDefault(inspection.getId(), 0L),
            nameOf(inspection)));
    }

    private static void validate(HistoryFilter filter) {
        if (filter.flat() != null && filter.flat().length() > MAX_FLAT_FILTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Flat filter is too long");
        }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
    }

    private static Specification<InspectionEntity> matching(Long residenceId, HistoryFilter filter) {
        return (root, query, cb) -> {
            var flat = root.join("flat");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(flat.get("residence").get("id"), residenceId));
            predicates.add(root.get("status").in(DONE));
            if (StringUtils.hasText(filter.flat())) {
                predicates.add(cb.like(cb.lower(flat.get("label")),
                    "%" + escapeLike(filter.flat().strip().toLowerCase()) + "%", '\\'));
            }
            if (filter.result() != null) {
                predicates.add(cb.equal(root.get("overallResult"), filter.result()));
            }
            if (filter.followUp() != null) {
                predicates.add(filter.followUp() ? cb.greaterThan(root.get("round"), 0) : cb.equal(root.get("round"), 0));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("inspectedAt"), startOf(filter.from())));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("inspectedAt"), startOf(filter.to().plusDays(1))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String nameOf(InspectionEntity inspection) {
        return inspection.getInspector() == null ? null : inspection.getInspector().getName();
    }

    /** Sortierbare Spalten der Historie. */
    public enum OrderBy {
        DATE("inspectedAt"), FLAT("flat.label"), KIND("round"), RESULT("overallResult"), INSPECTOR("inspector.name");

        private final String property;

        OrderBy(String property) {
            this.property = property;
        }
    }

    /** Standard: neueste zuerst. Bei gleichen Werten entscheidet das Datum (neueste zuerst), dann die ID. */
    public record HistoryOrder(OrderBy by, Sort.Direction direction) {
        public HistoryOrder {
            by = by == null ? OrderBy.DATE : by;
            direction = direction == null ? (by == OrderBy.DATE ? Sort.Direction.DESC : Sort.Direction.ASC) : direction;
        }

        Sort toSort() {
            var primary = Sort.by(new Sort.Order(direction, by.property));
            var tieBreak = by == OrderBy.DATE
                ? Sort.by(new Sort.Order(direction, "id"))
                : Sort.by(Sort.Order.desc("inspectedAt"), Sort.Order.desc("id"));
            return primary.and(tieBreak);
        }
    }

    /** Alle Felder sind optional. {@code from}/{@code to} sind Kalendertage (Europe/Berlin), beide inklusive. */
    public record HistoryFilter(String flat, Result result, Boolean followUp, LocalDate from, LocalDate to) {}

    public record HistoryEntry(Long id, String flatLabel, Instant inspectedAt, int round, Status status,
        Result overallResult, long deficiencies, String inspectorName) {}
}
