package de.seezeit.aume.inspection;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<InspectionEntity, Long> {
    Page<InspectionEntity> findByFlat_Residence_IdAndStatusIn(Long residenceId,
        Collection<InspectionEntity.Status> statuses, Pageable pageable);
}
