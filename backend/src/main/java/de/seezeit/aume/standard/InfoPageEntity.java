package de.seezeit.aume.standard;

import java.time.Instant;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** Zentral gepflegte Informationen, z. B. zu Ablauf und Konsequenzen einer Kontrolle. */
@Entity
@Table(name = "info_pages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InfoPageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 20000)
    private String body;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", nullable = false)
    private UserEntity updatedBy;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public InfoPageEntity(String slug, String title, String body, UserEntity updatedBy) {
        this.slug = slug;
        this.title = title;
        this.body = body;
        this.updatedBy = updatedBy;
    }
}
