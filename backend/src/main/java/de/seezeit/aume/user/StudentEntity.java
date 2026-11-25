package de.seezeit.aume.user;

import java.util.Set;
import de.seezeit.aume.residence.ResidenceEntity;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@DiscriminatorValue("STUDENT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentEntity extends UserEntity {
    public StudentEntity(String userKey, String name, Set<RoleEntity> roles, ResidenceEntity residence) {
        super(userKey, name, roles, residence);
    }
}
