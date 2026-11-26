package de.seezeit.aume.inspection;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Lesezugriff für Ergebnisse an Bewohnende; nur über {@link InspectionResultService}. */
interface ResidentResultRepository extends Repository<InspectionEntity, Long> {
    List<InspectionEntity> findByFlatIdAndStatusOrderByPublishedAtDesc(Long flatId, InspectionEntity.Status status);

    List<InspectionEntity> findByFlatIdAndStatusAndPublishedAtGreaterThanEqualOrderByPublishedAtDesc(Long flatId,
        InspectionEntity.Status status, Instant notBefore);

    boolean existsByIdAndFlatIdAndStatusAndPublishedAtGreaterThanEqual(Long id, Long flatId,
        InspectionEntity.Status status, Instant notBefore);
}
