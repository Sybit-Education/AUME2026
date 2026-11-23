package de.seezeit.aume;

import java.time.Instant;
import java.util.List;
import de.seezeit.aume.standard.ChecklistService;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.ChecklistVersionRepository;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.standard.CriterionRepository;
import de.seezeit.aume.standard.CriterionTranslationEntity;
import de.seezeit.aume.standard.CriterionTranslationRepository;
import de.seezeit.aume.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entwicklungsdaten für die Checkliste der Bewohnenden (US-21, US-22): die Kriterien aus dem
 * Wireframe und ihre englische Übersetzung. Legt die Checkliste nur an, wenn noch keine Fassung
 * existiert; die Kontroll-Testdaten der Objektbetreuung verwenden dieselben Kriterien.
 * Die verbindlichen Texte legt Seezeit fest.
 */
@Component
@Profile("dev")
@Order(3)
public class ChecklistSampleData implements CommandLineRunner {
    private record Criterion(String area, String title, String description,
            String areaEn, String titleEn, String descriptionEn) {}

    private static final List<Criterion> CRITERIA = List.of(
        new Criterion("Küche", "Arbeitsflächen",
            "Arbeitsflächen sind frei von Essensresten, Flecken und klebrigen Rückständen.",
            "Kitchen", "Work surfaces",
            "Work surfaces are free of food leftovers, stains and sticky residue."),
        new Criterion("Küche", "Herd & Backofen",
            "Keine eingebrannten Reste und keine sichtbare Fettschicht auf Herd, Backofen und Dunstabzug.",
            "Kitchen", "Hob & oven",
            "No burnt-on residue and no visible layer of grease on the hob, oven and extractor hood."),
        new Criterion("Küche", "Spüle",
            "Spüle und Armatur sind sauber. Es steht kein schmutziges Geschirr in der Spüle.",
            "Kitchen", "Sink",
            "The sink and tap are clean. There are no dirty dishes in the sink."),
        new Criterion("Küche", "Mülltrennung",
            "Müll ist richtig getrennt. Die Behälter sind nicht überfüllt und werden regelmäßig geleert.",
            "Kitchen", "Waste separation",
            "Waste is separated correctly. The bins are not overflowing and are emptied regularly."),
        new Criterion("Boden", "Flurboden",
            "Der Boden im Flur ist frei von Schmutz, Krümeln und abgestellten Gegenständen.",
            "Floors", "Hallway floor",
            "The hallway floor is free of dirt, crumbs and items left lying around."),
        new Criterion("Boden", "Küchenboden",
            "Der Küchenboden ist gefegt und gewischt. Es gibt keine klebrigen Flecken.",
            "Floors", "Kitchen floor",
            "The kitchen floor is swept and mopped. There are no sticky stains."),
        new Criterion("Treppe", "Stufen & Podeste",
            "Stufen und Podeste sind sauber und frei von Gegenständen. Fluchtwege bleiben frei.",
            "Stairs", "Steps & landings",
            "Steps and landings are clean and free of items. Escape routes stay clear."),
        new Criterion("Treppe", "Geländer & Handlauf",
            "Geländer und Handlauf sind staub- und fleckenfrei.",
            "Stairs", "Railing & handrail",
            "The railing and handrail are free of dust and stains."),
        new Criterion("Bad", "Waschbecken & Armaturen",
            "Keine sichtbaren Kalk- oder Schmutzablagerungen an Becken, Armaturen und Spiegel.",
            "Bathroom", "Washbasin & taps",
            "No visible limescale or dirt on the basin, taps and mirror."),
        new Criterion("Bad", "Dusche & WC",
            "Dusche und WC sind hygienisch sauber. Es gibt keine Schimmelstellen und keine Haare im Abfluss.",
            "Bathroom", "Shower & toilet",
            "The shower and toilet are hygienically clean. There is no mould and no hair in the drain."));

    private final UserRepository users;
    private final ChecklistVersionRepository versions;
    private final CriterionRepository criteria;
    private final CriterionTranslationRepository translations;
    private final ChecklistService checklists;

    public ChecklistSampleData(UserRepository users, ChecklistVersionRepository versions,
            CriterionRepository criteria, CriterionTranslationRepository translations, ChecklistService checklists) {
        this.users = users;
        this.versions = versions;
        this.criteria = criteria;
        this.translations = translations;
        this.checklists = checklists;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (versions.count() == 0) seedChecklist();
        checklists.currentPublished().ifPresent(this::seedEnglish);
    }

    private void seedChecklist() {
        var version = new ChecklistVersionEntity(1, users.findByUserKey("clerk").orElseThrow());
        version.setStatus(ChecklistVersionEntity.Status.PUBLISHED);
        version.setPublishedAt(Instant.now());
        versions.save(version);
        for (int i = 0; i < CRITERIA.size(); i++) {
            var criterion = CRITERIA.get(i);
            criteria.save(new CriterionEntity(version, criterion.area(), criterion.title(),
                criterion.description(), i + 1));
        }
    }

    /** Ergänzt fehlende englische Texte für Kriterien, deren deutscher Titel im Wireframe steht. */
    private void seedEnglish(ChecklistVersionEntity version) {
        for (var criterion : checklists.criteria(version)) {
            if (translations.existsByCriterionIdAndLanguage(criterion.getId(), "en")) continue;
            CRITERIA.stream()
                .filter(sample -> sample.area().equals(criterion.getArea()) && sample.title().equals(criterion.getTitle()))
                .findFirst()
                .ifPresent(sample -> translations.save(new CriterionTranslationEntity(criterion, "en",
                    sample.areaEn(), sample.titleEn(), sample.descriptionEn())));
        }
    }
}
