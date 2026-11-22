package de.seezeit.aume.residence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlatRepository extends JpaRepository<FlatEntity, Long> {
    List<FlatEntity> findByResidenceIdInOrderByLabel(Collection<Long> residenceIds);
    Optional<FlatEntity> findByResidenceIdAndLabel(Long residenceId, String label);
}
