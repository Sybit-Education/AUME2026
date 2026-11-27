package de.seezeit.aume.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Kontrollzeitraum einer Wohnanlage, dessen Abstimmung mit der Objektbetreuung und Ankündigung. Eine reguläre Kontrolle
 * umfasst alle freien WGs der Anlage, eine individuelle nur die ausgewählten (US-18b).
 */
@Entity
@Table(name = "inspection_periods")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionPeriodEntity {
    /** Stand der Abstimmung. Ob angekündigt wurde, zeigt {@code announcedAt}. */
    public enum Status { WAITING_FOR_CARETAKER, COUNTER_PROPOSED, AGREED }

    public enum Kind { REGULAR, INDIVIDUAL }

    /** Anlass einer individuellen Kontrolle. */
    public enum Occasion { SAMPLE, COMPLAINT, OTHER }

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

    /** Zeitpunkt der letzten erneuten Ankündigung mit verändertem Start- oder Enddatum. */
    private Instant rescheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.WAITING_FOR_CARETAKER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind = Kind.REGULAR;

    /** Nur bei {@link Kind#INDIVIDUAL} gesetzt. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Occasion occasion;

    /** Person, die dem Zeitraum zustimmen muss. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caretaker_id")
    private UserEntity caretaker;

    private LocalTime windowStart;

    private LocalTime windowEnd;

    private LocalDate counterStartsOn;

    private LocalDate counterEndsOn;

    private LocalTime counterWindowStart;

    private LocalTime counterWindowEnd;

    @Column(length = 2000)
    private String counterReason;

    /** Verhindert, dass jemand einem inzwischen geänderten Zeitraum zustimmt. */
    @Version
    private long version;

    public InspectionPeriodEntity(ResidenceEntity residence, LocalDate startsOn, LocalDate endsOn,
            UserEntity createdBy) {
        this.residence = residence;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.createdBy = createdBy;
    }
}
