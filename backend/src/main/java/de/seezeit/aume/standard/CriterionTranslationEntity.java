package de.seezeit.aume.standard;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Übersetzung eines Prüfkriteriums (US-21). Die deutsche Fassung steht im Kriterium selbst;
 * fehlt eine Übersetzung, zeigt die Oberfläche den deutschen Text.
 */
@Entity
@Table(name = "checklist_criterion_translations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"criterion_id", "language"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CriterionTranslationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", nullable = false)
    private CriterionEntity criterion;

    /** ISO-639-1-Sprachcode, aktuell nur {@code en}. */
    @Column(nullable = false, length = 8)
    private String language;

    @Column(nullable = false, length = 80)
    private String area;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    public CriterionTranslationEntity(CriterionEntity criterion, String language, String area, String title,
            String description) {
        this.criterion = criterion;
        this.language = language;
        this.area = area;
        this.title = title;
        this.description = description;
    }
}
