package de.seezeit.aume.inspection;

import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionRepository extends JpaRepository<InspectionEntity, Long>,
        JpaSpecificationExecutor<InspectionEntity> {
    Page<InspectionEntity> findByFlat_Residence_IdAndStatusIn(Long residenceId,
        Collection<InspectionEntity.Status> statuses, Pageable pageable);

    /**
     * Sperrt die Kontrolle bis zum Ende der Transaktion. Alle Änderungen an einer Kontrolle und
     * ihren Befunden laufen darüber, damit gleichzeitige Anfragen nacheinander ausgeführt werden.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InspectionEntity i where i.id = :id")
    Optional<InspectionEntity> findLockedById(@Param("id") Long id);
}
