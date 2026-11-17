package de.seezeit.aume.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "privileges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivilegeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    public PrivilegeEntity(String name) {
        this.name = name;
    }
}
