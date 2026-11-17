package de.seezeit.aume.result;

import de.seezeit.aume.attachment.AttachmentEntity;
import de.seezeit.aume.residence.MembershipEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Foto der Bewohnenden vom Zustand eines Bereichs beim Einzug. */
@Entity
@Table(name = "handover_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoverPhotoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private MembershipEntity membership;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false, unique = true)
    private AttachmentEntity attachment;

    @Column(nullable = false, length = 80)
    private String area;

    public HandoverPhotoEntity(MembershipEntity membership, AttachmentEntity attachment, String area) {
        this.membership = membership;
        this.attachment = attachment;
        this.area = area;
    }
}
