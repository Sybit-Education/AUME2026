package de.seezeit.aume.residence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "residences", collectionResourceRel = "residences")
public interface ResidenceRepository extends JpaRepository<ResidenceEntity, Long> {

	Page<ResidenceEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

}
