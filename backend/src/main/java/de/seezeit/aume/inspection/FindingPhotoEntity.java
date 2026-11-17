package de.seezeit.aume.inspection;

import de.seezeit.aume.attachment.AttachmentEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "finding_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FindingPhotoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id", nullable = false)
    private FindingEntity finding;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false, unique = true)
    private AttachmentEntity attachment;

    public FindingPhotoEntity(FindingEntity finding, AttachmentEntity attachment) {
        this.finding = finding;
        this.attachment = attachment;
    }
}
