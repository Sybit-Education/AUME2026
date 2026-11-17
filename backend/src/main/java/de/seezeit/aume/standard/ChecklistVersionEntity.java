package de.seezeit.aume.standard;

import java.time.Instant;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Nach der Veröffentlichung unveränderlich; geänderte Kriterien ergeben eine neue Fassung. */
@Entity
@Table(name = "checklist_versions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistVersionEntity {
    public enum Status { DRAFT, PUBLISHED, RETIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    public ChecklistVersionEntity(int versionNo, UserEntity createdBy) {
        this.versionNo = versionNo;
        this.createdBy = createdBy;
    }
}
