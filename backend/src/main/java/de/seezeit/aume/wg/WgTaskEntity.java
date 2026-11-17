package de.seezeit.aume.wg;

import java.time.Instant;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.standard.CriterionEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Private Putzaufgabe einer WG; nur für deren Mitglieder sichtbar, nicht für Personalrollen. */
@Entity
@Table(name = "wg_tasks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WgTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "flat_id", nullable = false)
    private FlatEntity flat;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id")
    private CriterionEntity criterion;

    @Column(nullable = false)
    private int position;

    private Instant archivedAt;

    public WgTaskEntity(FlatEntity flat, String title, int position) {
        this.flat = flat;
        this.title = title;
        this.position = position;
    }
}
