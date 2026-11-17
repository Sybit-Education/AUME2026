package de.seezeit.aume.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUserKey(String userKey);
    Optional<UserEntity> findByIdentityIssuerAndIdentitySubject(String issuer, String subject);
}
