package de.seezeit.aume.standard;

import de.seezeit.aume.attachment.AttachmentEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Vergleichsbild „sauber genug“ (GOOD) oder „nicht ausreichend“ (BAD). */
@Entity
@Table(name = "criterion_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CriterionImageEntity {
    public enum Kind { GOOD, BAD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", nullable = false)
    private CriterionEntity criterion;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false, unique = true)
    private AttachmentEntity attachment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    public CriterionImageEntity(CriterionEntity criterion, AttachmentEntity attachment, Kind kind) {
        this.criterion = criterion;
        this.attachment = attachment;
        this.kind = kind;
    }
}
