package de.seezeit.aume.wg;

import java.time.Instant;
import java.time.LocalDate;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.user.UserEntity;
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

    /** Manually assigned person. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", nullable = false)
    private UserEntity assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WgTaskRhythm rhythm;

    /** Monday of the first due week. */
    @Column(nullable = false)
    private LocalDate startWeek;

    private Instant archivedAt;

    public WgTaskEntity(FlatEntity flat, String title, int position, UserEntity assignee,
                        WgTaskRhythm rhythm, LocalDate startWeek) {
        this.flat = flat;
        this.title = title;
        this.position = position;
        this.assignee = assignee;
        this.rhythm = rhythm;
        this.startWeek = startWeek;
    }
}
