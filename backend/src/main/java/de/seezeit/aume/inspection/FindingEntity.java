package de.seezeit.aume.inspection;

import de.seezeit.aume.standard.CriterionEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bewertung eines Kriteriums in einer Kontrolle. Ein Mangel ist ein Befund mit {@code DEFICIENT};
 * {@code RESOLVED_ON_SITE} wurde vor Ort geklärt und zählt nicht als Mangel.
 */
@Entity
@Table(name = "inspection_findings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"inspection_id", "criterion_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FindingEntity {
    public enum Rating { OK, DEFICIENT, RESOLVED_ON_SITE }
    public enum Severity { LIGHT, SEVERE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private InspectionEntity inspection;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", nullable = false)
    private CriterionEntity criterion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rating rating;

    /** Nur bei {@code DEFICIENT}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Severity severity;

    @Column(length = 2000)
    private String note;

    @Column(nullable = false)
    private boolean buildingCauseSuspected;

    /** Ursprünglicher Mangel, den diese Nachkontrolle erneut prüft. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_finding_id")
    private FindingEntity previousFinding;

    public FindingEntity(InspectionEntity inspection, CriterionEntity criterion, Rating rating) {
        this.inspection = inspection;
        this.criterion = criterion;
        this.rating = rating;
    }
}
