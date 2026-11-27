package de.seezeit.aume.inspection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    /**
     * Kontrollen, die die Objektbetreuung jetzt durchführen kann: begonnene immer, geplante nur, wenn ihr Zeitraum
     * angekündigt ist oder sie keinen Zeitraum haben (z. B. Nachkontrollen). Nach Beginn des Zeitraums sortiert,
     * Kontrollen ohne Zeitraum zuletzt.
     */
    @Query(value = """
        select i from InspectionEntity i left join i.period p
        where i.flat.residence.id in :residenceIds
          and (i.status = :started or (i.status = :planned and (p is null or p.announcedAt is not null)))
        order by case when p.startsOn is null then 1 else 0 end, p.startsOn, i.flat.label, i.id""",
        countQuery = """
        select count(i) from InspectionEntity i left join i.period p
        where i.flat.residence.id in :residenceIds
          and (i.status = :started or (i.status = :planned and (p is null or p.announcedAt is not null)))""")
    Page<InspectionEntity> findDue(@Param("residenceIds") Collection<Long> residenceIds,
        @Param("planned") InspectionEntity.Status planned, @Param("started") InspectionEntity.Status started,
        Pageable pageable);

    boolean existsByFlat_Id(Long flatId);

    /**
     * Sperrt die Kontrolle bis zum Ende der Transaktion. Alle Änderungen an einer Kontrolle und
     * ihren Befunden laufen darüber, damit gleichzeitige Anfragen nacheinander ausgeführt werden.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InspectionEntity i where i.id = :id")
    Optional<InspectionEntity> findLockedById(@Param("id") Long id);

    /** Abgeschlossene Kontrollen einer WG vor {@code before}, neueste zuerst (US-51). */
    @Query("""
        select i from InspectionEntity i
        where i.flat.id = :flatId and i.status in :statuses and i.id <> :excludedId and i.inspectedAt < :before
        order by i.inspectedAt desc, i.id desc""")
    List<InspectionEntity> findPrevious(@Param("flatId") Long flatId,
        @Param("statuses") Collection<InspectionEntity.Status> statuses, @Param("excludedId") Long excludedId,
        @Param("before") Instant before, Pageable limit);

    /** Angekündigte WG-Kontrollen mit noch nicht abgelaufenem Zeitraum. */
    @Query("""
        select i from InspectionEntity i join fetch i.period p join fetch i.flat f
        where i.id > :afterId and i.status in :statuses
          and p.announcedAt is not null and p.endsOn >= :today
          and p.residence = f.residence
        order by i.id
        """)
    List<InspectionEntity> findAnnouncedNotificationCandidates(@Param("afterId") long afterId,
        @Param("today") LocalDate today, @Param("statuses") Collection<InspectionEntity.Status> statuses,
        Pageable limit);
}
