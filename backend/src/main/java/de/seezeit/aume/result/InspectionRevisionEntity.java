package de.seezeit.aume.result;

import java.time.Instant;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Zuordenbare Korrektur eines veröffentlichten Ergebnisses; {@code snapshot} enthält es als JSON. */
@Entity
@Table(name = "inspection_revisions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"inspection_id", "revision_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionRevisionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private InspectionEntity inspection;

    @Column(nullable = false)
    private int revisionNo;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(nullable = false, length = 100000)
    private String snapshot;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public InspectionRevisionEntity(InspectionEntity inspection, int revisionNo, String reason,
            String snapshot, UserEntity createdBy) {
        this.inspection = inspection;
        this.revisionNo = revisionNo;
        this.reason = reason;
        this.snapshot = snapshot;
        this.createdBy = createdBy;
    }
}
