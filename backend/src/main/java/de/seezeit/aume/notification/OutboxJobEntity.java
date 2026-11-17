package de.seezeit.aume.notification;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Hintergrundauftrag der transaktionalen Outbox (ADR-0008). Muss in derselben Transaktion wie das
 * auslösende Fachereignis geschrieben werden; der Worker beansprucht ihn über {@code lockedUntil}.
 */
@Entity
@Table(name = "outbox_jobs", indexes = @Index(columnList = "status, next_attempt_at"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxJobEntity {
    public enum Status { PENDING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 10000)
    private String payload;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Instant lockedUntil;

    @Column(length = 2000)
    private String lastError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public OutboxJobEntity(String type, String payload, String idempotencyKey, Instant nextAttemptAt) {
        this.type = type;
        this.payload = payload;
        this.idempotencyKey = idempotencyKey;
        this.nextAttemptAt = nextAttemptAt;
    }
}
