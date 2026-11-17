package de.seezeit.aume.residence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResidenceRepository extends JpaRepository<ResidenceEntity, Long> {
    Optional<ResidenceEntity> findFirstByName(String name);
    Page<ResidenceEntity> findById(Long id, Pageable pageable);
}
