package de.seezeit.aume.residence;

import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Zuständigkeit von Objektbetreuung und Sachbearbeitung für eine Wohnanlage. */
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

    public StaffAssignmentEntity(UserEntity user, ResidenceEntity residence) {
        this.user = user;
        this.residence = residence;
    }
}
