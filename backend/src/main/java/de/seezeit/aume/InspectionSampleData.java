package de.seezeit.aume;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.InspectionRepository;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.ChecklistVersionRepository;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.standard.CriterionRepository;
import de.seezeit.aume.user.UserRepository;

/**
 * Entwicklungsdaten für die Kontrolle: eine veröffentlichte Checkliste nach dem Wireframe,
 * je eine geplante Kontrolle für die WGs S-01 und S-02 der Wohnanlage Sonne. Läuft nach {@link SampleData},
 * das die Wohnanlage und die WGs anlegt.
 */
@Component
@Profile("dev")
@Order(2)
public class InspectionSampleData implements CommandLineRunner {
    private record Criterion(String area, String title, String description) {}

    private static final List<Criterion> CRITERIA = List.of(
        new Criterion("Küche", "Arbeitsflächen",
            "Arbeitsflächen sind frei von Essensresten, Flecken und klebrigen Rückständen."),
        new Criterion("Küche", "Herd & Backofen",
            "Keine eingebrannten Reste und keine sichtbare Fettschicht auf Herd, Backofen und Dunstabzug."),
        new Criterion("Küche", "Spüle",
            "Spüle und Armatur sind sauber. Es steht kein schmutziges Geschirr in der Spüle."),
        new Criterion("Küche", "Mülltrennung",
            "Müll ist richtig getrennt. Die Behälter sind nicht überfüllt und werden regelmäßig geleert."),
        new Criterion("Boden", "Flurboden",
            "Der Boden im Flur ist frei von Schmutz, Krümeln und abgestellten Gegenständen."),
        new Criterion("Boden", "Küchenboden",
            "Der Küchenboden ist gefegt und gewischt. Es gibt keine klebrigen Flecken."),
        new Criterion("Treppe", "Stufen & Podeste",
            "Stufen und Podeste sind sauber und frei von Gegenständen. Fluchtwege bleiben frei."),
        new Criterion("Treppe", "Geländer & Handlauf",
            "Geländer und Handlauf sind staub- und fleckenfrei."),
        new Criterion("Bad", "Waschbecken & Armaturen",
            "Keine sichtbaren Kalk- oder Schmutzablagerungen an Becken, Armaturen und Spiegel."),
        new Criterion("Bad", "Dusche & WC",
            "Dusche und WC sind hygienisch sauber. Es gibt keine Schimmelstellen und keine Haare im Abfluss."));

    private final ResidenceRepository residences;
    private final UserRepository users;
    private final FlatRepository flats;
    private final ChecklistVersionRepository versions;
    private final CriterionRepository criteria;
    private final InspectionRepository inspections;

    public InspectionSampleData(ResidenceRepository residences, UserRepository users, FlatRepository flats,
            ChecklistVersionRepository versions, CriterionRepository criteria, InspectionRepository inspections) {
        this.residences = residences;
        this.users = users;
        this.flats = flats;
        this.versions = versions;
        this.criteria = criteria;
        this.inspections = inspections;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (versions.count() == 0) seedChecklist();
        if (inspections.count() == 0) {
            var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
            inspections.save(new InspectionEntity(flat(residence, "S-01", 4), null));
            inspections.save(new InspectionEntity(flat(residence, "S-02", 3), null));
        }
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

    private FlatEntity flat(ResidenceEntity residence, String label, int capacity) {
        return flats.findByResidenceIdAndLabel(residence.getId(), label)
            .orElseGet(() -> flats.save(new FlatEntity(residence, label, capacity)));
    }
}
