package de.seezeit.aume.result;

import java.time.Instant;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Maßnahme nach wiederholtem Nichtbestehen: Reinigungsfirma oder Abmahnung. */
@Entity
@Table(name = "escalations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EscalationEntity {
    public enum Kind { CLEANING_COMPANY, WARNING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private InspectionEntity inspection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(length = 2000)
    private String note;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public EscalationEntity(InspectionEntity inspection, Kind kind, String note, UserEntity createdBy) {
        this.inspection = inspection;
        this.kind = kind;
        this.note = note;
        this.createdBy = createdBy;
    }
}
