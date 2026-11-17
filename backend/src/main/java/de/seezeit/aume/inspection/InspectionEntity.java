package de.seezeit.aume.inspection;

import java.time.Instant;
import java.time.LocalDate;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Kontrolle einer WG. Nachkontrollen haben {@code round} 1 oder 2 und verweisen auf die vorherige
 * Kontrolle. Das Gesamtergebnis setzt ausschließlich die prüfende Person, nie das System.
 */
@Entity
@Table(name = "inspections")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionEntity {
    public enum Status { PLANNED, DRAFT, FINALIZED, PUBLISHED }
    public enum Result { PASSED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "flat_id", nullable = false)
    private FlatEntity flat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    private InspectionPeriodEntity period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_inspection_id")
    private InspectionEntity parentInspection;

    @Column(nullable = false)
    private int round;

    /** Ab Status DRAFT gesetzt und danach unveränderlich. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_version_id")
    private ChecklistVersionEntity checklistVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspector_id")
    private UserEntity inspector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "co_inspector_id")
    private UserEntity coInspector;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PLANNED;

    /** Pflicht ab Status FINALIZED. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Result overallResult;

    /** Frist einer Nachkontrolle. */
    private LocalDate dueOn;

    private Instant inspectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private UserEntity publishedBy;

    private Instant publishedAt;

    /** Kontrolle vor dem Auszug, die neuen Mietenden als Übergabezustand angezeigt wird. */
    @Column(nullable = false)
    private boolean handover;

    @Version
    private long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public InspectionEntity(FlatEntity flat, InspectionPeriodEntity period) {
        this.flat = flat;
        this.period = period;
    }

    public InspectionEntity(InspectionEntity parentInspection, LocalDate dueOn) {
        this.flat = parentInspection.getFlat();
        this.parentInspection = parentInspection;
        this.round = parentInspection.getRound() + 1;
        this.dueOn = dueOn;
    }
}
