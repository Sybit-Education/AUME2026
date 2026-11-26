package de.seezeit.aume.residence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@code validTo} is the first day without access. */
public interface MembershipRepository extends JpaRepository<MembershipEntity, Long> {
    @Query("""
        select m from MembershipEntity m join fetch m.flat f join fetch f.residence
        where m.user.id = :userId and m.validFrom <= :today and (m.validTo is null or m.validTo > :today)
        """)
    Optional<MembershipEntity> findActive(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Query("""
        select m from MembershipEntity m join fetch m.user u
        where m.flat.id = :flatId and m.validFrom <= :today and (m.validTo is null or m.validTo > :today)
        order by u.name, u.id
        """)
    List<MembershipEntity> findActiveInFlat(@Param("flatId") Long flatId, @Param("today") LocalDate today);
}
