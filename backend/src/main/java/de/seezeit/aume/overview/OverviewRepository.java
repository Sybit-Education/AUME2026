package de.seezeit.aume.overview;

import java.util.Collection;
import java.util.List;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.InspectionEntity.Status;
import de.seezeit.aume.residence.StaffKind;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Lesender Zugriff der Übersicht auf Kontrollen. Bewusst getrennt vom {@code InspectionRepository}
 * des Kontroll-Features (PR #61), damit die Übersicht keine Schreibzugriffe auf fremde Moduldaten erhält.
 */
public interface OverviewRepository extends Repository<InspectionEntity, Long> {
    /** Kontrollen der Anlagen, für die die Person mit einer der angegebenen Zuständigkeiten eingetragen ist. */
    @Query("""
        select i from InspectionEntity i
        join fetch i.flat f
        join fetch f.residence
        left join fetch i.period
        where i.status in :statuses and exists (select 1 from StaffAssignmentEntity s
                      where s.residence = f.residence and s.user.id = :userId and s.kind in :kinds)""")
    List<InspectionEntity> findByAssignedStaff(@Param("userId") Long userId,
        @Param("kinds") Collection<StaffKind> kinds, @Param("statuses") Collection<Status> statuses);
}
