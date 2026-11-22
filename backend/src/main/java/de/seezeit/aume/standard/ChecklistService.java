package de.seezeit.aume.standard;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Schnittstelle des Moduls {@code standard} für andere Module, zum Beispiel für Kontrollen. */
@Service
@Transactional(readOnly = true)
public class ChecklistService {
    private final ChecklistVersionRepository versions;
    private final CriterionRepository criteria;

    public ChecklistService(ChecklistVersionRepository versions, CriterionRepository criteria) {
        this.versions = versions;
        this.criteria = criteria;
    }

    /** Neueste veröffentlichte Fassung; an sie wird eine neu gestartete Kontrolle gebunden. */
    public Optional<ChecklistVersionEntity> currentPublished() {
        return versions.findFirstByStatusOrderByVersionNoDesc(ChecklistVersionEntity.Status.PUBLISHED);
    }

    /** Kriterien einer Fassung in der festgelegten Reihenfolge. */
    public List<CriterionEntity> criteria(ChecklistVersionEntity version) {
        return criteria.findByChecklistVersionIdOrderByPositionAsc(version.getId());
    }
}
