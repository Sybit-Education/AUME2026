package de.seezeit.aume.standard;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistVersionRepository extends JpaRepository<ChecklistVersionEntity, Long> {
    Optional<ChecklistVersionEntity> findFirstByStatusOrderByVersionNoDesc(ChecklistVersionEntity.Status status);
}
