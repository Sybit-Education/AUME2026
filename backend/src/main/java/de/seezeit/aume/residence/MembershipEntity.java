package de.seezeit.aume.residence;

import java.time.LocalDate;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bewohnende einer WG; {@code validTo == null} bedeutet aktuell eingezogen. */
@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "flat_id", nullable = false)
    private FlatEntity flat;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private LocalDate validFrom;

    private LocalDate validTo;

    public MembershipEntity(FlatEntity flat, UserEntity user, LocalDate validFrom) {
        this.flat = flat;
        this.user = user;
        this.validFrom = validFrom;
    }
}
