package de.seezeit.aume.planning;

import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionPeriodRepository extends JpaRepository<InspectionPeriodEntity, Long> {
    boolean existsByResidenceId(Long residenceId);

    /** Noch nicht angekündigte Zeiträume und angekündigte, die noch nicht vorbei sind. */
    @Query(value = """
        select p from InspectionPeriodEntity p
        join fetch p.residence
        left join fetch p.caretaker
        where p.residence.id in :residenceIds and (p.announcedAt is null or p.endsOn >= :today)
        order by p.startsOn, p.id
        """, countQuery = """
        select count(p) from InspectionPeriodEntity p
        where p.residence.id in :residenceIds and (p.announcedAt is null or p.endsOn >= :today)
        """)
    Page<InspectionPeriodEntity> findPlanned(@Param("residenceIds") Collection<Long> residenceIds,
        @Param("today") LocalDate today, Pageable pageable);

    @Query(value = """
        select p from InspectionPeriodEntity p
        join fetch p.residence
        join fetch p.caretaker c
        where c.id = :caretakerId and p.announcedAt is null
        order by p.startsOn, p.id
        """, countQuery = """
        select count(p) from InspectionPeriodEntity p
        where p.caretaker.id = :caretakerId and p.announcedAt is null
        """)
    Page<InspectionPeriodEntity> findAssigned(@Param("caretakerId") Long caretakerId, Pageable pageable);
}
