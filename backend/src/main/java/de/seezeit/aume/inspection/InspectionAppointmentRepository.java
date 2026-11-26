package de.seezeit.aume.inspection;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface InspectionAppointmentRepository extends Repository<InspectionEntity, Long> {
    @Query("""
        select i from InspectionEntity i join fetch i.period p
        where i.status = :status and p.announcedAt is not null and p.endsOn >= :today
          and p.residence = i.flat.residence
          and exists (
              select m.id from MembershipEntity m
              where m.user.userKey = :userKey and m.flat = i.flat
                and m.validFrom <= :today and (m.validTo is null or m.validTo > :today)
          )
          and 1 = (
              select count(distinct m.flat.id) from MembershipEntity m
              where m.user.userKey = :userKey
                and m.validFrom <= :today and (m.validTo is null or m.validTo > :today)
          )
        order by p.startsOn, i.id
        """)
    List<InspectionEntity> findUpcoming(@Param("userKey") String userKey,
            @Param("today") LocalDate today, @Param("status") InspectionEntity.Status status, Pageable page);

    @Query("""
        select i from InspectionEntity i join fetch i.period p
        where i.flat.id = :flatId and p.residence = i.flat.residence
          and p.announcedAt is not null and p.endsOn >= :today and i.status in :statuses
        order by p.startsOn, i.id
        """)
    List<InspectionEntity> findOpenDetails(@Param("flatId") Long flatId, @Param("today") LocalDate today,
            @Param("statuses") List<InspectionEntity.Status> statuses, Pageable page);

    @Query("""
        select i from InspectionEntity i join fetch i.period p
        where i.flat.id = :flatId and p.residence = i.flat.residence and p.announcedAt is not null
        order by p.startsOn desc, i.id
        """)
    List<InspectionEntity> findLatestDetails(@Param("flatId") Long flatId, Pageable page);
}
