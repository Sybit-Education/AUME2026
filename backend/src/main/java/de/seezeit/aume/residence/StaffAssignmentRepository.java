package de.seezeit.aume.residence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignmentEntity, Long> {
    List<StaffAssignmentEntity> findByResidenceIdInAndKindIn(Collection<Long> residenceIds, Collection<StaffKind> kinds);
    Optional<StaffAssignmentEntity> findByResidenceIdAndKind(Long residenceId, StaffKind kind);
    boolean existsByResidenceIdAndUserIdAndKind(Long residenceId, Long userId, StaffKind kind);
    List<StaffAssignmentEntity> findByUserIdAndKindIn(Long userId, Collection<StaffKind> kinds);
}
