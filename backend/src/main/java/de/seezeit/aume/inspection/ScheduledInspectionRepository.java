package de.seezeit.aume.inspection;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Zugriff der Kontrollplanung auf WG-Kontrollen; nur über {@link InspectionSchedulingService}. */
interface ScheduledInspectionRepository extends Repository<InspectionEntity, Long> {
    InspectionEntity save(InspectionEntity inspection);

    List<InspectionEntity> findByPeriodId(Long periodId);

    void deleteAll(Iterable<? extends InspectionEntity> inspections);

    @Query("select distinct i.flat.id from InspectionEntity i where i.flat.id in :flatIds and i.status in :statuses")
    Set<Long> findFlatIdsWithStatus(@Param("flatIds") Collection<Long> flatIds,
        @Param("statuses") Collection<InspectionEntity.Status> statuses);

    @Query("""
        select i.period.id as periodId, count(i) as flatCount from InspectionEntity i
        where i.period.id in :periodIds group by i.period.id
        """)
    List<PeriodFlatCount> countByPeriod(@Param("periodIds") Collection<Long> periodIds);

    interface PeriodFlatCount {
        Long getPeriodId();
        long getFlatCount();
    }
}
