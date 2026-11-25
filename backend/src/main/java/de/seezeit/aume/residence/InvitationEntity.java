package de.seezeit.aume.residence;

import java.time.Instant;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Einmalige, befristete Einladung in eine WG. Es wird nur der Hash des Tokens gespeichert. */
@Entity
@Table(name = "invitations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvitationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "flat_id", nullable = false)
    private FlatEntity flat;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private UserEntity usedBy;

    private Instant usedAt;

    private Instant revokedAt;

    public InvitationEntity(FlatEntity flat, String tokenHash, Instant expiresAt, UserEntity createdBy) {
        this.flat = flat;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }
}
