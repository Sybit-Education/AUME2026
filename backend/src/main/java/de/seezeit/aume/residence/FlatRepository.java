package de.seezeit.aume.residence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlatRepository extends JpaRepository<FlatEntity, Long> {
    Optional<FlatEntity> findByResidenceIdAndLabel(Long residenceId, String label);
}
