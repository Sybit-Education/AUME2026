package de.seezeit.aume;

import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.UserEntity;
import de.seezeit.aume.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(2)
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
            entities.persist(new InspectionEntity(flat, period));
        }
    }
}
