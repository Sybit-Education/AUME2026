package de.seezeit.aume.wg;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WgSelfCheckRepository extends JpaRepository<WgSelfCheckEntity, Long> {
    List<WgSelfCheckEntity> findByFlatId(Long flatId);

    Optional<WgSelfCheckEntity> findByFlatIdAndCriterionId(Long flatId, Long criterionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WgSelfCheckEntity c where c.flat.id = :flatId")
    int deleteByFlat(@Param("flatId") Long flatId);
}
