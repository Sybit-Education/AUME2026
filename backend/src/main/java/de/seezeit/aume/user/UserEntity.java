package de.seezeit.aume.user;

import java.util.HashSet;
import java.util.Set;
import de.seezeit.aume.residence.ResidenceEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"identity_issuer", "identity_subject"}))
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String userKey;

    @Column(nullable = false)
    private String name;

    @Column(name = "identity_issuer", length = 512)
    private String identityIssuer;

    @Column(name = "identity_subject")
    private String identitySubject;

    @ManyToMany
    @JoinTable(name = "users_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"}))
    private Set<RoleEntity> roles = new HashSet<>();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private ResidenceEntity residence;

    protected UserEntity(String userKey, String name, Set<RoleEntity> roles, ResidenceEntity residence) {
        this.userKey = userKey;
        this.name = name;
        this.roles.addAll(roles);
        this.residence = residence;
    }

    public void linkIdentity(String issuer, String subject) {
        this.identityIssuer = issuer;
        this.identitySubject = subject;
    }
}
