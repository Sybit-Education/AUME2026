package de.seezeit.aume.result;

import java.time.Instant;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Rückfrage der WG oder Antwort der Sachbearbeitung zu einer Kontrolle. */
@Entity
@Table(name = "inspection_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionCommentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private InspectionEntity inspection;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity author;

    @Column(nullable = false, length = 4000)
    private String body;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public InspectionCommentEntity(InspectionEntity inspection, UserEntity author, String body) {
        this.inspection = inspection;
        this.author = author;
        this.body = body;
    }
}
