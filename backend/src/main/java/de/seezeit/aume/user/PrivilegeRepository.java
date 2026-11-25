package de.seezeit.aume.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivilegeRepository extends JpaRepository<PrivilegeEntity, Long> {
    Optional<PrivilegeEntity> findByName(String name);
}
