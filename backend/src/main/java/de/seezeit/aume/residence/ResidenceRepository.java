package de.seezeit.aume.residence;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResidenceRepository extends JpaRepository<ResidenceEntity, Long> {
    Optional<ResidenceEntity> findFirstByName(String name);
    Page<ResidenceEntity> findById(Long id, Pageable pageable);

    /** Anlagen, für die die Person mit der angegebenen Zuständigkeit eingetragen ist. */
    @Query("""
        select r from ResidenceEntity r
        where exists (select 1 from StaffAssignmentEntity s
                      where s.residence = r and s.user.id = :userId and s.kind = :kind)
        """)
    Page<ResidenceEntity> findAssigned(@Param("userId") Long userId, @Param("kind") StaffKind kind, Pageable pageable);

    /** Anlagen, für die die Person eine der angegebenen Zuständigkeiten hat. */
    @Query("""
        select r from ResidenceEntity r
        where exists (select 1 from StaffAssignmentEntity s
                      where s.residence = r and s.user.id = :userId and s.kind in :kinds)
        """)
    Page<ResidenceEntity> findAssignedByKinds(@Param("userId") Long userId,
                                             @Param("kinds") Collection<StaffKind> kinds,
                                             Pageable pageable);
}
