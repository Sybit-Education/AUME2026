package de.seezeit.aume.standard;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfoPageRepository extends JpaRepository<InfoPageEntity, Long> {
    List<InfoPageEntity> findBySlugIn(Collection<String> slugs);
}
