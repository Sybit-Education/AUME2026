package de.seezeit.aume.standard;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Prüfkriterium je Bereich; die Beschreibung sagt, wann es erfüllt ist und wie tief geprüft wird. */
@Entity
@Table(name = "checklist_criteria")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CriterionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_version_id", nullable = false)
    private ChecklistVersionEntity checklistVersion;

    @Column(nullable = false, length = 80)
    private String area;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(nullable = false)
    private int position;

    public CriterionEntity(ChecklistVersionEntity checklistVersion, String area, String title,
            String description, int position) {
        this.checklistVersion = checklistVersion;
        this.area = area;
        this.title = title;
        this.description = description;
        this.position = position;
    }
}
