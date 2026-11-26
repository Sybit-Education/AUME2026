package de.seezeit.aume.wg;

import java.time.Instant;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein im Selbstcheck abgehaktes Kriterium einer WG (US-84). Nur für die WG selbst; fließt nicht in die
 * offizielle Bewertung ein. Einträge vor dem letzten Kontrollergebnis gelten als zurückgesetzt.
 */
@Entity
@Table(name = "wg_self_checks", uniqueConstraints = @UniqueConstraint(columnNames = {"flat_id", "criterion_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WgSelfCheckEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "flat_id", nullable = false)
    private FlatEntity flat;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", nullable = false)
    private CriterionEntity criterion;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by_id", nullable = false)
    private UserEntity checkedBy;

    @Column(nullable = false)
    private Instant checkedAt;

    public WgSelfCheckEntity(FlatEntity flat, CriterionEntity criterion, UserEntity checkedBy, Instant checkedAt) {
        this.flat = flat;
        this.criterion = criterion;
        this.checkedBy = checkedBy;
        this.checkedAt = checkedAt;
    }
}
