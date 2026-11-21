package de.seezeit.aume.standard;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriterionRepository extends JpaRepository<CriterionEntity, Long> {
    List<CriterionEntity> findByChecklistVersionIdOrderByPositionAsc(Long checklistVersionId);
}
