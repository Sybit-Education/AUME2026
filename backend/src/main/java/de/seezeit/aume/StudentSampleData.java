package de.seezeit.aume;

import de.seezeit.aume.inspection.FindingEntity;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.user.UserEntity;
import de.seezeit.aume.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(4)
public class StudentSampleData implements CommandLineRunner {
    private final ResidenceRepository residences;
    private final UserRepository users;
    private final EntityManager entities;

    public StudentSampleData(ResidenceRepository residences, UserRepository users, EntityManager entities) {
        this.residences = residences;
        this.users = users;
        this.entities = entities;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        var student = users.findByUserKey("student").orElseThrow();
        var clerk = users.findByUserKey("clerk").orElseThrow();
        seedInspection(residence, student, clerk);
        seedPublishedResult(student, users.findByUserKey("caretaker").orElseThrow());
    }

    /**
     * US-38: Veröffentlichte Erstkontrolle nach dem Einzug mit zwei Mängeln (einer mit vermuteter
     * Gebäudeursache), damit der Reiter „Ergebnis“ lokal etwas zeigt. Die Kontrollen aus
     * {@link InspectionSampleData} liegen vor dem Einzug und sind für Studierende nicht sichtbar.
     * Wird nur einmal angelegt.
     */
    private void seedPublishedResult(UserEntity student, UserEntity caretaker) {
        var membership = entities.createQuery("""
            select m from MembershipEntity m where m.user = :student and m.validTo is null order by m.validFrom
            """, MembershipEntity.class).setParameter("student", student).setMaxResults(1).getSingleResultOrNull();
        if (membership == null) return;
        var flat = membership.getFlat();
        var movedIn = membership.getValidFrom().atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant();
        var existing = entities.createQuery("""
            select count(i) from InspectionEntity i
            where i.flat = :flat and i.status = :status and i.publishedAt >= :movedIn
            """, Long.class).setParameter("flat", flat).setParameter("status", InspectionEntity.Status.PUBLISHED)
            .setParameter("movedIn", movedIn).getSingleResult();
        if (existing > 0) return;

        var version = entities.createQuery("""
            select v from ChecklistVersionEntity v where v.status = :status order by v.versionNo desc
            """, ChecklistVersionEntity.class).setParameter("status", ChecklistVersionEntity.Status.PUBLISHED)
            .setMaxResults(1).getSingleResultOrNull();
        if (version == null) return;
        var criteria = entities.createQuery("""
            select c from CriterionEntity c where c.checklistVersion = :version order by c.position
            """, CriterionEntity.class).setParameter("version", version).getResultList();

        var now = Instant.now();
        var inspectedAt = now.isBefore(movedIn) ? movedIn : now;
        var inspection = new InspectionEntity(flat, null);
        inspection.setChecklistVersion(version);
        inspection.setInspector(caretaker);
        inspection.setInspectedAt(inspectedAt);
        inspection.setOverallResult(InspectionEntity.Result.FAILED);
        inspection.setStatus(InspectionEntity.Status.PUBLISHED);
        inspection.setPublishedBy(caretaker);
        inspection.setPublishedAt(inspectedAt);
        entities.persist(inspection);

        for (var criterion : criteria) {
            var finding = new FindingEntity(inspection, criterion, FindingEntity.Rating.OK);
            switch (criterion.getTitle()) {
                case "Herd & Backofen" -> {
                    finding.setRating(FindingEntity.Rating.DEFICIENT);
                    finding.setSeverity(FindingEntity.Severity.SEVERE);
                    finding.setNote("Eingebrannte Fettreste im Backofen.");
                }
                case "Dusche & WC" -> {
                    finding.setRating(FindingEntity.Rating.DEFICIENT);
                    finding.setSeverity(FindingEntity.Severity.LIGHT);
                    finding.setNote("Schimmel an der Silikonfuge, vermutlich durch die defekte Lüftung.");
                    finding.setBuildingCauseSuspected(true);
                }
                default -> { }
            }
            entities.persist(finding);
        }
    }

    private void seedInspection(ResidenceEntity residence, UserEntity student, UserEntity clerk) {
        var activeMemberships = entities.createQuery("""
            select m from MembershipEntity m where m.user = :student and m.validTo is null
            """, MembershipEntity.class).setParameter("student", student).getResultList();
        var flat = entities.createQuery("""
            select f from FlatEntity f where f.residence = :residence and f.label = 'S-01'
            """, FlatEntity.class).setParameter("residence", residence).getSingleResultOrNull();
        for (var active : activeMemberships) {
            if (flat == null || !flat.getId().equals(active.getFlat().getId())) return;
        }
        if (flat == null) {
            flat = new FlatEntity(residence, "S-01", 1);
            entities.persist(flat);
        }
        if (activeMemberships.isEmpty()) {
            entities.persist(new MembershipEntity(flat, student, LocalDate.now()));
        }

        var startsOn = LocalDate.of(2026, 12, 7);
        var endsOn = LocalDate.of(2026, 12, 11);
        var period = entities.createQuery("""
            select p from InspectionPeriodEntity p
            where p.residence = :residence and p.startsOn = :startsOn and p.endsOn = :endsOn
              and (p.announcedAt is not null or not exists (
                  select i.id from InspectionEntity i where i.period = p and i.flat <> :flat
              ))
            order by case when p.announcedAt is null then 1 else 0 end, p.id
            """, InspectionPeriodEntity.class)
            .setParameter("residence", residence)
            .setParameter("startsOn", startsOn)
            .setParameter("endsOn", endsOn)
            .setParameter("flat", flat)
            .setMaxResults(1).getSingleResultOrNull();
        if (period == null) {
            period = new InspectionPeriodEntity(residence, startsOn, endsOn, clerk);
            entities.persist(period);
        }
        if (period.getAnnouncedAt() == null) {
            period.setAnnouncedBy(clerk);
            period.setAnnouncedAt(Instant.now());
        }

        var count = entities.createQuery("""
            select count(i) from InspectionEntity i where i.flat = :flat and i.period = :period and i.round = 0
            """, Long.class).setParameter("flat", flat).setParameter("period", period).getSingleResult();
        if (count == 0) {
            var candidates = entities.createQuery("""
                select i from InspectionEntity i
                where i.flat = :flat and i.round = 0 and i.status = :status
                  and i.period is null and i.inspectedAt is null
                """, InspectionEntity.class)
                .setParameter("flat", flat)
                .setParameter("status", InspectionEntity.Status.PLANNED)
                .setMaxResults(2).getResultList();
            if (candidates.size() == 1) {
                candidates.getFirst().setPeriod(period);
            } else {
                entities.persist(new InspectionEntity(flat, period));
            }
        }
    }
}
