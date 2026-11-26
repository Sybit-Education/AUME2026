package de.seezeit.aume.wg;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import de.seezeit.aume.inspection.InspectionResultService;
import de.seezeit.aume.residence.MembershipService;
import de.seezeit.aume.standard.ChecklistService;
import de.seezeit.aume.standard.CriterionEntity;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Selbstcheck der WG mit der veröffentlichten Checkliste (US-84).
 *
 * <p>Sichtbar und änderbar nur für Mitglieder der eigenen WG: die WG kommt immer aus der aktiven
 * Mitgliedschaft, nie aus der Anfrage. Seezeit hat keinen Endpunkt dafür, und die Einträge fließen nicht in
 * die offizielle Bewertung ein. Nach jedem veröffentlichten Kontrollergebnis beginnt der Selbstcheck neu:
 * ältere Einträge zählen nicht mehr und werden beim nächsten Abhaken überschrieben.
 */
@Service
@Transactional(readOnly = true)
public class WgSelfCheckService {
    public static final String INVALID_CRITERION = "invalid_criterion";

    private final WgSelfCheckRepository checks;
    private final MembershipService memberships;
    private final ChecklistService checklists;
    private final InspectionResultService results;
    private final EntityManager entities;

    public WgSelfCheckService(WgSelfCheckRepository checks, MembershipService memberships, ChecklistService checklists,
                              InspectionResultService results, EntityManager entities) {
        this.checks = checks;
        this.memberships = memberships;
        this.checklists = checklists;
        this.results = results;
        this.entities = entities;
    }

    /** Abgehakte Kriterien der aktuellen Checkliste seit dem letzten Kontrollergebnis. */
    public SelfCheckView view(String userKey) {
        var flat = memberships.requireActive(userKey).flat();
        var current = currentCriterionIds();
        var since = results.latestPublication(flat.getId()).orElse(Instant.MIN);
        var checked = checks.findByFlatId(flat.getId()).stream()
            .filter(check -> !check.getCheckedAt().isBefore(since))
            .map(check -> check.getCriterion().getId())
            .filter(current::contains)
            .sorted()
            .toList();
        return new SelfCheckView(checked);
    }

    @Transactional
    public void check(String userKey, Long criterionId) {
        var active = memberships.requireActive(userKey);
        if (criterionId == null || !currentCriterionIds().contains(criterionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_CRITERION);
        }
        var now = Instant.now();
        var existing = checks.findByFlatIdAndCriterionId(active.flat().getId(), criterionId);
        if (existing.isPresent()) {
            // Ein Eintrag aus der Zeit vor dem letzten Ergebnis gilt als neu abgehakt.
            var since = results.latestPublication(active.flat().getId()).orElse(Instant.MIN);
            if (existing.get().getCheckedAt().isBefore(since)) {
                existing.get().setCheckedAt(now);
                existing.get().setCheckedBy(active.user());
            }
            return;
        }
        checks.save(new WgSelfCheckEntity(active.flat(), entities.getReference(CriterionEntity.class, criterionId),
            active.user(), now));
    }

    @Transactional
    public void uncheck(String userKey, Long criterionId) {
        var flat = memberships.requireActive(userKey).flat();
        checks.findByFlatIdAndCriterionId(flat.getId(), criterionId).ifPresent(checks::delete);
    }

    /** „Zurücksetzen“: alle Haken der eigenen WG entfernen. */
    @Transactional
    public void reset(String userKey) {
        checks.deleteByFlat(memberships.requireActive(userKey).flat().getId());
    }

    private Set<Long> currentCriterionIds() {
        return checklists.currentPublished().stream()
            .flatMap(version -> checklists.criteria(version).stream())
            .map(CriterionEntity::getId)
            .collect(Collectors.toSet());
    }

    public record SelfCheckView(List<Long> checkedCriterionIds) {}
}
