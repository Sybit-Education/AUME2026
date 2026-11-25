package de.seezeit.aume.audit;

import java.time.Instant;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Unveränderliches Änderungsereignis; enthält keine Daten der privaten WG-Organisation. */
@Entity
@Table(name = "audit_events", indexes = @Index(columnList = "entity_type, entity_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private UserEntity actor;

    @Column(nullable = false, length = 80)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 10000)
    private String details;

    public AuditEventEntity(UserEntity actor, String entityType, Long entityId, String action, String details) {
        this.actor = actor;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.details = details;
    }
}
