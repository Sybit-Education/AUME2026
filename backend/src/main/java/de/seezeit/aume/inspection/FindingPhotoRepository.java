package de.seezeit.aume.inspection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Fotos zu Mängeln (US-35); nur innerhalb des Moduls {@code inspection}. */
interface FindingPhotoRepository extends JpaRepository<FindingPhotoEntity, Long> {
    List<FindingPhotoEntity> findByFinding_IdInOrderByIdAsc(Collection<Long> findingIds);

    List<FindingPhotoEntity> findByFinding_Id(Long findingId);

    long countByFinding_Id(Long findingId);

    boolean existsByAttachment_Id(Long attachmentId);

    Optional<FindingPhotoEntity> findByIdAndFinding_Inspection_Id(Long id, Long inspectionId);
}
