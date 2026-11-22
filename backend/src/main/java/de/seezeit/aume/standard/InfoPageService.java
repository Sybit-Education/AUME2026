package de.seezeit.aume.standard;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.user.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Die drei zentral gepflegten Texte für Bewohnende (US-58). */
@Service
@Transactional(readOnly = true)
public class InfoPageService {
    private static final String PROCESS = "inspection-process";
    private static final String CONSEQUENCES = "defect-consequences";
    private static final String STANDARDS = "cleanliness-standards";
    private static final int MAX_LENGTH = 20000;

    private final InfoPageRepository pages;
    private final AppUserService users;

    public InfoPageService(InfoPageRepository pages, AppUserService users) {
        this.pages = pages;
        this.users = users;
    }

    public InfoPagesView read() {
        return view(load());
    }

    @Transactional
    public InfoPagesView update(String userKey, InfoPagesRequest request) {
        validate(request.process());
        validate(request.consequences());
        validate(request.standards());
        var editor = users.requireUser(userKey);
        var found = load();
        store(found, PROCESS, "Ablauf der Kontrolle", request.process(), editor);
        store(found, CONSEQUENCES, "Folgen bei Mängeln", request.consequences(), editor);
        store(found, STANDARDS, "Sauberkeitsstandards", request.standards(), editor);
        pages.flush();
        return view(found);
    }

    private Map<String, InfoPageEntity> load() {
        var found = new HashMap<String, InfoPageEntity>();
        pages.findBySlugIn(List.of(PROCESS, CONSEQUENCES, STANDARDS))
            .forEach(page -> found.put(page.getSlug(), page));
        return found;
    }

    private void store(Map<String, InfoPageEntity> found, String slug, String title, String body, UserEntity editor) {
        var page = found.get(slug);
        if (page == null) {
            found.put(slug, pages.save(new InfoPageEntity(slug, title, body, editor)));
        } else if (!page.getBody().equals(body)) {
            page.setBody(body);
            page.setUpdatedBy(editor);
        }
    }

    private InfoPagesView view(Map<String, InfoPageEntity> found) {
        var updatedAt = found.values().stream().map(InfoPageEntity::getUpdatedAt)
            .max(Comparator.naturalOrder()).orElse(null);
        return new InfoPagesView(body(found, PROCESS), body(found, CONSEQUENCES), body(found, STANDARDS), updatedAt);
    }

    private static String body(Map<String, InfoPageEntity> found, String slug) {
        var page = found.get(slug);
        return page == null ? "" : page.getBody();
    }

    private static void validate(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "info_text_required");
        }
        if (text.length() > MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "info_text_too_long");
        }
    }

    public record InfoPagesRequest(String process, String consequences, String standards) {}

    /** Enthält bewusst keine Angabe, wer zuletzt bearbeitet hat: Bewohnende lesen dieselbe Antwort. */
    public record InfoPagesView(String process, String consequences, String standards, Instant updatedAt) {}
}
