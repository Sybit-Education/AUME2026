package de.seezeit.aume.standard;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.user.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Die drei zentral gepflegten Texte für Bewohnende (US-58), jeweils auf Deutsch und Englisch. */
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
        validateEnglish(request.processEn());
        validateEnglish(request.consequencesEn());
        validateEnglish(request.standardsEn());
        var editor = users.requireUser(userKey);
        var found = load();
        store(found, PROCESS, "Ablauf der Kontrolle", request.process(), request.processEn(), editor);
        store(found, CONSEQUENCES, "Folgen bei Mängeln", request.consequences(), request.consequencesEn(), editor);
        store(found, STANDARDS, "Sauberkeitsstandards", request.standards(), request.standardsEn(), editor);
        pages.flush();
        return view(found);
    }

    private Map<String, InfoPageEntity> load() {
        var found = new HashMap<String, InfoPageEntity>();
        pages.findBySlugIn(List.of(PROCESS, CONSEQUENCES, STANDARDS))
            .forEach(page -> found.put(page.getSlug(), page));
        return found;
    }

    private void store(Map<String, InfoPageEntity> found, String slug, String title, String body, String bodyEn,
            UserEntity editor) {
        var page = found.get(slug);
        if (page == null) {
            page = pages.save(new InfoPageEntity(slug, title, body, editor));
            page.setBodyEn(bodyEn);
            found.put(slug, page);
        } else if (!page.getBody().equals(body) || !Objects.equals(page.getBodyEn(), bodyEn)) {
            page.setBody(body);
            page.setBodyEn(bodyEn);
            page.setUpdatedBy(editor);
        }
    }

    private InfoPagesView view(Map<String, InfoPageEntity> found) {
        var updatedAt = found.values().stream().map(InfoPageEntity::getUpdatedAt)
            .max(Comparator.naturalOrder()).orElse(null);
        return new InfoPagesView(body(found, PROCESS), body(found, CONSEQUENCES), body(found, STANDARDS),
            bodyEn(found, PROCESS), bodyEn(found, CONSEQUENCES), bodyEn(found, STANDARDS), updatedAt);
    }

    private static String body(Map<String, InfoPageEntity> found, String slug) {
        var page = found.get(slug);
        return page == null ? "" : page.getBody();
    }

    private static String bodyEn(Map<String, InfoPageEntity> found, String slug) {
        var page = found.get(slug);
        return page == null || page.getBodyEn() == null ? "" : page.getBodyEn();
    }

    private static void validate(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "info_text_required");
        }
        if (text.length() > MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "info_text_too_long");
        }
    }

    /** Englisch ist optional; fehlt es, bleibt die englische Fassung leer. */
    private static void validateEnglish(String text) {
        if (text != null && text.length() > MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "info_text_too_long");
        }
    }

    public record InfoPagesRequest(String process, String consequences, String standards,
            String processEn, String consequencesEn, String standardsEn) {
        public InfoPagesRequest(String process, String consequences, String standards) {
            this(process, consequences, standards, null, null, null);
        }
    }

    /**
     * Enthält bewusst keine Angabe, wer zuletzt bearbeitet hat: Bewohnende lesen dieselbe Antwort.
     * {@code …En} ist die englische Fassung, leer, solange keine gespeichert wurde.
     */
    public record InfoPagesView(String process, String consequences, String standards,
        String processEn, String consequencesEn, String standardsEn, Instant updatedAt) {}
}
