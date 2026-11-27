package de.seezeit.aume.standard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lesesicht der aktuell veröffentlichten Checkliste für Bewohnende (US-21, US-22).
 * Es sind dieselben Kriterien, mit denen die Objektbetreuung kontrolliert.
 */
@Service
@Transactional(readOnly = true)
public class ResidentChecklistService {
    private final ChecklistService checklists;
    private final CriterionTranslationRepository translations;

    public ResidentChecklistService(ChecklistService checklists, CriterionTranslationRepository translations) {
        this.checklists = checklists;
        this.translations = translations;
    }

    /** Leer, solange Seezeit noch keine Checkliste veröffentlicht hat. */
    public Optional<ChecklistView> current(Locale locale) {
        return checklists.currentPublished().map(version -> view(version, locale));
    }

    /** Lesesicht einer konkreten aktuellen oder früher veröffentlichten Fassung. */
    public Optional<ChecklistView> forPublishedVersion(ChecklistVersionEntity version, Locale locale) {
        return isPublishedVersion(version) ? Optional.of(view(version, locale)) : Optional.empty();
    }

    static boolean isPublishedVersion(ChecklistVersionEntity version) {
        return version != null && (version.getStatus() == ChecklistVersionEntity.Status.PUBLISHED
            || version.getStatus() == ChecklistVersionEntity.Status.RETIRED);
    }

    private ChecklistView view(ChecklistVersionEntity version, Locale locale) {
        var criteria = checklists.criteria(version);
        var translated = translatedTexts(criteria, locale);
        var areas = new LinkedHashMap<String, List<CriterionView>>();
        for (var criterion : criteria) {
            var text = translated.getOrDefault(criterion.getId(), new Text(criterion.getArea(),
                criterion.getTitle(), criterion.getDescription()));
            areas.computeIfAbsent(text.area(), ignored -> new ArrayList<>())
                .add(new CriterionView(criterion.getId(), text.title(), text.description()));
        }
        var areaViews = areas.entrySet().stream()
            .map(entry -> new AreaView(entry.getKey(), List.copyOf(entry.getValue())))
            .toList();
        return new ChecklistView(version.getVersionNo(), areaViews);
    }

    private Map<Long, Text> translatedTexts(List<CriterionEntity> criteria, Locale locale) {
        var language = locale == null ? "" : locale.getLanguage();
        if (language.isEmpty() || Locale.GERMAN.getLanguage().equals(language) || criteria.isEmpty()) {
            return Map.of();
        }
        var ids = criteria.stream().map(CriterionEntity::getId).toList();
        return translations.findByCriterionIdInAndLanguage(ids, language).stream()
            .collect(Collectors.toMap(translation -> translation.getCriterion().getId(),
                translation -> new Text(translation.getArea(), translation.getTitle(), translation.getDescription()),
                (first, second) -> first));
    }

    private record Text(String area, String title, String description) {}

    public record ChecklistView(int version, List<AreaView> areas) {}

    public record AreaView(String name, List<CriterionView> criteria) {}

    public record CriterionView(Long id, String title, String description) {}
}
