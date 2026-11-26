package de.seezeit.aume.wg;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Each row is a task completed by a person in a week. */
public interface WgTaskAssignmentRepository extends JpaRepository<WgTaskAssignmentEntity, Long> {
    @Query("""
        select a from WgTaskAssignmentEntity a join fetch a.user
        where a.task.id in :taskIds and a.weekStart = :week
        """)
    List<WgTaskAssignmentEntity> findDone(@Param("taskIds") Collection<Long> taskIds, @Param("week") LocalDate week);

    Optional<WgTaskAssignmentEntity> findByTaskIdAndWeekStart(Long taskId, LocalDate weekStart);
}
