package de.seezeit.aume.wg;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WgTaskRepository extends JpaRepository<WgTaskEntity, Long> {
    @Query("""
        select t from WgTaskEntity t join fetch t.assignee
        where t.flat.id = :flatId and t.archivedAt is null
        order by t.position, t.id
        """)
    List<WgTaskEntity> findActive(@Param("flatId") Long flatId);

    /** Scoped to the flat, so foreign task IDs are not found. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WgTaskEntity> findByIdAndFlatIdAndArchivedAtIsNull(Long id, Long flatId);

    long countByFlatId(Long flatId);
}
