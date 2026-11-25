package de.seezeit.aume.attachment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Schreibt Entwicklungsdateien nach devdata/uploads. Der Dateiname ist ein zufälliger Schlüssel. */
@Component
@Profile("dev")
public class FileAttachmentStorage {
    private final Path root;

    public FileAttachmentStorage(@Value("${aume.storage.location}") String location) {
        this.root = Path.of(location).toAbsolutePath().normalize();
    }

    public String save(MultipartFile file) {
        var key = UUID.randomUUID().toString();
        var target = resolve(key);
        try (var input = file.getInputStream()) {
            Files.createDirectories(root);
            Files.copy(input, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return key;
    }

    public Resource load(String storageKey) {
        return new FileSystemResource(resolve(storageKey));
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String storageKey) {
        var key = UUID.fromString(storageKey).toString();
        if (!key.equals(storageKey)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        var path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
