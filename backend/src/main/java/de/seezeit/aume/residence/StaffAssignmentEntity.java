package de.seezeit.aume.residence;

import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Zuständigkeit einer Person für eine Wohnanlage: Objektbetreuung, Vertretung oder Sachbearbeitung.
 * Eine Person hat pro Anlage höchstens eine Zuständigkeit.
 */
@Entity
@Table(name = "staff_assignments", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "residence_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffAssignmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private ResidenceEntity residence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffKind kind;

    public StaffAssignmentEntity(UserEntity user, ResidenceEntity residence, StaffKind kind) {
        this.user = user;
        this.residence = residence;
        this.kind = kind;
    }
}
