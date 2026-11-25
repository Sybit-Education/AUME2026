package de.seezeit.aume.wg;

import java.time.Instant;
import java.time.LocalDate;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Zuteilung einer Aufgabe für eine Woche (Rotation) und deren Erledigung. */
@Entity
@Table(name = "wg_task_assignments", uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "week_start"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WgTaskAssignmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private WgTaskEntity task;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private LocalDate weekStart;

    private Instant completedAt;

    public WgTaskAssignmentEntity(WgTaskEntity task, UserEntity user, LocalDate weekStart) {
        this.task = task;
        this.user = user;
        this.weekStart = weekStart;
    }
}
