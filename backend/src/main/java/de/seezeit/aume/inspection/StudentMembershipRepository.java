package de.seezeit.aume.inspection;

import java.time.LocalDate;
import java.util.List;
import de.seezeit.aume.residence.MembershipEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Aktive WG-Mitgliedschaften einer Person (eingezogen, nicht ausgezogen), wie beim Kontrolltermin. */
interface StudentMembershipRepository extends Repository<MembershipEntity, Long> {
    @Query("""
        select m from MembershipEntity m join fetch m.flat
        where m.user.userKey = :userKey
          and m.validFrom <= :today and (m.validTo is null or m.validTo > :today)
        order by m.validFrom, m.id
        """)
    List<MembershipEntity> findActive(@Param("userKey") String userKey, @Param("today") LocalDate today);
}
