package de.seezeit.aume.user;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String name;

    @ManyToMany
    @JoinTable(name = "roles_privileges",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "privilege_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "privilege_id"}))
    private Set<PrivilegeEntity> privileges = new HashSet<>();

    public RoleEntity(String name, Set<PrivilegeEntity> privileges) {
        this.name = name;
        this.privileges.addAll(privileges);
    }
}
