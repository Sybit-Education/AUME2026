package de.seezeit.aume.inspection;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FindingRepository extends JpaRepository<FindingEntity, Long> {
    List<FindingEntity> findByInspectionId(Long inspectionId);
    Optional<FindingEntity> findByInspectionIdAndCriterionId(Long inspectionId, Long criterionId);
}
