package de.seezeit.aume;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import de.seezeit.aume.inspection.FindingEntity;
import de.seezeit.aume.inspection.FindingRepository;
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
import de.seezeit.aume.user.UserEntity;
import de.seezeit.aume.user.UserRepository;

/**
 * Entwicklungsdaten für die Kontrolle: eine veröffentlichte Checkliste nach dem Wireframe,
 * je eine geplante Kontrolle für die WGs S-01 und S-02 der Wohnanlage Sonne und drei abgeschlossene
 * Kontrollen für die Kontroll-Historie, dazu eine geplante Kontrolle in Petershausen für die Vertretung (US-03).
 * Läuft nach {@link SampleData}, das die Wohnanlagen und die WGs anlegt.
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
    private final FindingRepository findings;

    public InspectionSampleData(ResidenceRepository residences, UserRepository users, FlatRepository flats,
            ChecklistVersionRepository versions, CriterionRepository criteria, InspectionRepository inspections,
            FindingRepository findings) {
        this.residences = residences;
        this.users = users;
        this.flats = flats;
        this.versions = versions;
        this.criteria = criteria;
        this.inspections = inspections;
        this.findings = findings;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (versions.count() == 0) seedChecklist();
        if (inspections.count() == 0) {
            var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
            inspections.save(new InspectionEntity(flat(residence, "S-01", 4), null));
            inspections.save(new InspectionEntity(flat(residence, "S-02", 3), null));
            seedHistory(residence);
        }
        seedDeputyExample();
    }

    /**
     * US-03: eine sofort startbare Kontrolle in Petershausen (WG 3.09, ohne Zeitraum). So sieht caretaker2, der
     * Petershausen betreut und Sonne vertritt, im Browser Kontrollen beider Anlagen, caretaker nur die von Sonne.
     */
    private void seedDeputyExample() {
        residences.findFirstByName("Petershausen").ifPresent(petershausen -> {
            var flat = flat(petershausen, "3.09", 5);
            if (!inspections.existsByFlat_Id(flat.getId())) inspections.save(new InspectionEntity(flat, null));
        });
    }

    /** Abgeschlossene Kontrollen: S-01 nicht bestanden mit Nachkontrolle bestanden, S-02 bestanden. */
    private void seedHistory(ResidenceEntity residence) {
        var version = versions.findFirstByStatusOrderByVersionNoDesc(ChecklistVersionEntity.Status.PUBLISHED)
            .orElseThrow();
        var list = criteria.findByChecklistVersionIdOrderByPositionAsc(version.getId());
        var caretaker = users.findByUserKey("caretaker").orElseThrow();
        var now = Instant.now();

        var failed = finalized(new InspectionEntity(flat(residence, "S-01", 4), null), version, caretaker,
            now.minus(Duration.ofDays(30)), InspectionEntity.Result.FAILED);
        for (var criterion : list) {
            var finding = new FindingEntity(failed, criterion, FindingEntity.Rating.OK);
            if (criterion.getTitle().equals("Herd & Backofen")) {
                finding.setRating(FindingEntity.Rating.DEFICIENT);
                finding.setSeverity(FindingEntity.Severity.SEVERE);
                finding.setNote("Eingebrannte Fettschicht auf Herd und Dunstabzug.");
            } else if (criterion.getTitle().equals("Mülltrennung")) {
                finding.setRating(FindingEntity.Rating.DEFICIENT);
                finding.setSeverity(FindingEntity.Severity.LIGHT);
                finding.setNote("Gelber Sack mit Restmüll befüllt.");
            } else if (criterion.getTitle().equals("Dusche & WC")) {
                finding.setRating(FindingEntity.Rating.RESOLVED_ON_SITE);
                finding.setNote("Abfluss vor Ort gereinigt.");
            }
            findings.save(finding);
        }

        var followUp = new InspectionEntity(failed, null);
        finalized(followUp, version, caretaker, now.minus(Duration.ofDays(23)), InspectionEntity.Result.PASSED);
        list.forEach(criterion -> findings.save(new FindingEntity(followUp, criterion, FindingEntity.Rating.OK)));

        var passed = finalized(new InspectionEntity(flat(residence, "S-02", 3), null), version, caretaker,
            now.minus(Duration.ofDays(28)), InspectionEntity.Result.PASSED);
        list.forEach(criterion -> findings.save(new FindingEntity(passed, criterion, FindingEntity.Rating.OK)));
    }

    private InspectionEntity finalized(InspectionEntity inspection, ChecklistVersionEntity version,
            UserEntity inspector, Instant inspectedAt, InspectionEntity.Result result) {
        inspection.setChecklistVersion(version);
        inspection.setInspector(inspector);
        inspection.setInspectedAt(inspectedAt);
        inspection.setOverallResult(result);
        inspection.setStatus(InspectionEntity.Status.PUBLISHED);
        inspection.setPublishedBy(inspector);
        inspection.setPublishedAt(inspectedAt);
        return inspections.save(inspection);
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
