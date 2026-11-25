package de.seezeit.aume.attachment;

import de.seezeit.aume.auth.AppUserService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Legt Metadaten in attachments ab und die Bytes im Entwicklungsordner. */
@Service
@Profile("dev")
@Transactional(readOnly = true)
public class AttachmentService {
    private final AttachmentRepository attachments;
    private final FileAttachmentStorage storage;
    private final AppUserService users;

    public AttachmentService(AttachmentRepository attachments, FileAttachmentStorage storage, AppUserService users) {
        this.attachments = attachments;
        this.storage = storage;
        this.users = users;
    }

    @Transactional
    public AttachmentView store(String userKey, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty");
        }
        var user = users.requireUser(userKey);
        var key = storage.save(file);
        try {
            var saved = attachments.save(new AttachmentEntity(key, contentType(file), file.getSize(), user));
            return new AttachmentView(saved.getId(), saved.getContentType(), saved.getSizeBytes());
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
    }

    public Download load(String userKey, Long id) {
        var user = users.requireUser(userKey);
        var attachment = attachments.findById(id)
            .filter(candidate -> candidate.getUploadedBy().getId().equals(user.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        var body = storage.load(attachment.getStorageKey());
        if (!body.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
        return new Download(body, attachment.getContentType(), attachment.getSizeBytes());
    }

    private static String contentType(MultipartFile file) {
        var type = file.getContentType();
        if (type == null || type.isBlank() || type.length() > 100) {
            return "application/octet-stream";
        }
        return type;
    }

    public record AttachmentView(Long id, String contentType, long sizeBytes) {}

    public record Download(Resource body, String contentType, long sizeBytes) {}
}
