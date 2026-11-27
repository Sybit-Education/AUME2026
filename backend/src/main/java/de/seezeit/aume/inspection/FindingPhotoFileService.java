package de.seezeit.aume.inspection;

import de.seezeit.aume.attachment.AttachmentRepository;
import de.seezeit.aume.attachment.FileAttachmentStorage;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Liefert die Datei eines Mangel-Fotos aus (US-35). Wer das Foto sehen darf, prüft vorher
 * {@link InspectionService#photoAttachment}: alle, die für die Anlage zuständig sind, nicht nur die
 * hochladende Person. Nur im Profil {@code dev}, solange die Ablage aus #79 nur dort existiert.
 */
@Service
@Profile("dev")
@Transactional(readOnly = true)
class FindingPhotoFileService {
    private final InspectionService inspections;
    private final AttachmentRepository attachments;
    private final FileAttachmentStorage storage;

    FindingPhotoFileService(InspectionService inspections, AttachmentRepository attachments,
            FileAttachmentStorage storage) {
        this.inspections = inspections;
        this.attachments = attachments;
        this.storage = storage;
    }

    PhotoFile load(String userKey, Long inspectionId, Long photoId) {
        var attachment = attachments.findById(inspections.photoAttachment(userKey, inspectionId, photoId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var body = storage.load(attachment.getStorageKey());
        if (!body.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new PhotoFile(body, attachment.getContentType(), attachment.getSizeBytes());
    }

    record PhotoFile(Resource body, String contentType, long sizeBytes) {}
}
