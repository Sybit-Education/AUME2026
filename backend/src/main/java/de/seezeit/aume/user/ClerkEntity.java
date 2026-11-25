package de.seezeit.aume.user;

import java.util.Set;
import de.seezeit.aume.residence.ResidenceEntity;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@DiscriminatorValue("CLERK")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClerkEntity extends UserEntity {
    public ClerkEntity(String userKey, String name, Set<RoleEntity> roles, ResidenceEntity residence) {
        super(userKey, name, roles, residence);
    }
}
