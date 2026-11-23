package de.seezeit.aume.standard;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriterionTranslationRepository extends JpaRepository<CriterionTranslationEntity, Long> {
    List<CriterionTranslationEntity> findByCriterionIdInAndLanguage(Collection<Long> criterionIds, String language);

    boolean existsByCriterionIdAndLanguage(Long criterionId, String language);
}
