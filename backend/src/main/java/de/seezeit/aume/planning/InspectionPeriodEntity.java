package de.seezeit.aume.planning;

import java.time.Instant;
import java.time.LocalDate;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Kontrollzeitraum einer Wohnanlage und dessen Ankündigung. */
@Entity
@Table(name = "inspection_periods")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionPeriodEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private ResidenceEntity residence;

    @Column(nullable = false)
    private LocalDate startsOn;

    @Column(nullable = false)
    private LocalDate endsOn;

    @Column(length = 2000)
    private String note;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announced_by")
    private UserEntity announcedBy;

    private Instant announcedAt;

    public InspectionPeriodEntity(ResidenceEntity residence, LocalDate startsOn, LocalDate endsOn,
            UserEntity createdBy) {
        this.residence = residence;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.createdBy = createdBy;
    }
}
