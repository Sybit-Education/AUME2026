package de.seezeit.aume.user;

import java.util.Set;
import de.seezeit.aume.residence.ResidenceEntity;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@DiscriminatorValue("CARETAKER")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaretakerEntity extends UserEntity {
    public CaretakerEntity(String userKey, String name, Set<RoleEntity> roles, ResidenceEntity residence) {
        super(userKey, name, roles, residence);
    }
}
