package de.seezeit.aume.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "users", collectionResourceRel = "users")
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Page<UserEntity> findByRole(Role role, Pageable pageable);

	Optional<UserEntity> findByUserKey(String userKey);

}
