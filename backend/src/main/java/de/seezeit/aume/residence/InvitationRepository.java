package de.seezeit.aume.residence;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InvitationRepository extends JpaRepository<InvitationEntity, Long> {
    boolean existsByTokenHash(String tokenHash);

    /** Locks the invitation so a code cannot be used twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvitationEntity> findByTokenHash(String tokenHash);
}
