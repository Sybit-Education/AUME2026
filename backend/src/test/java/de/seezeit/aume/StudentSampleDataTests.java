package de.seezeit.aume;

import static org.assertj.core.api.Assertions.assertThat;

import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.MembershipEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-student-sample-data-tests;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
@Transactional
class StudentSampleDataTests {
    @Autowired private StudentSampleData studentSampleData;
    @Autowired private EntityManager entities;

    @Test
    void sampleInspectionIsPersistedForTheStudentWithoutDuplicates() {
        var originalId = sampleInspection().getId();
        var inspectionCount = entities.createQuery("select count(i) from InspectionEntity i", Long.class)
            .getSingleResult();
        var periodCount = entities.createQuery("select count(p) from InspectionPeriodEntity p", Long.class)
            .getSingleResult();
        studentSampleData.run();
        studentSampleData.run();
        entities.flush();
        entities.clear();
        assertThat(entities.createQuery("select count(i) from InspectionEntity i", Long.class)
            .getSingleResult()).isEqualTo(inspectionCount);

        var memberships = entities.createQuery("""
            select m from MembershipEntity m where m.user.userKey = 'student' and m.validTo is null
            """, MembershipEntity.class).getResultList();
        assertThat(memberships).hasSize(1);
        var flat = memberships.getFirst().getFlat();
        assertThat(flat.getLabel()).isEqualTo("S-01");
        assertThat(flat.getResidence().getName()).isEqualTo("Sonnenbühl West I");

        var inspections = entities.createQuery("""
            select i from InspectionEntity i join i.period p
            where i.flat = :flat and i.round = 0 and p.startsOn = :startsOn and p.endsOn = :endsOn
            """, InspectionEntity.class)
            .setParameter("flat", flat)
            .setParameter("startsOn", LocalDate.of(2026, 12, 7))
            .setParameter("endsOn", LocalDate.of(2026, 12, 11))
            .getResultList();
        assertThat(inspections).hasSize(1);
        var inspection = inspections.getFirst();
        assertThat(inspection.getId()).isEqualTo(originalId);
        assertThat(inspection.getStatus()).isEqualTo(InspectionEntity.Status.PLANNED);
        assertThat(inspection.getInspectedAt()).isNull();
        var period = inspection.getPeriod();
        assertThat(period.getResidence().getId()).isEqualTo(flat.getResidence().getId());
        assertThat(period.getStartsOn()).isEqualTo(LocalDate.of(2026, 12, 7));
        assertThat(period.getEndsOn()).isEqualTo(LocalDate.of(2026, 12, 11));
        assertThat(period.getAnnouncedAt()).isNotNull();
        assertThat(period.getAnnouncedBy().getUserKey()).isEqualTo("clerk");
        assertThat(entities.createQuery("""
            select count(p) from InspectionPeriodEntity p
            where p.residence = :residence and p.startsOn = :startsOn and p.endsOn = :endsOn
            """, Long.class)
            .setParameter("residence", flat.getResidence())
            .setParameter("startsOn", period.getStartsOn())
            .setParameter("endsOn", period.getEndsOn())
            .getSingleResult()).isEqualTo(1L);
        assertThat(entities.createQuery("select count(p) from InspectionPeriodEntity p", Long.class)
            .getSingleResult()).isEqualTo(periodCount);
    }

    private InspectionEntity sampleInspection() {
        return entities.createQuery("""
            select i from InspectionEntity i join i.period p
            where i.flat.label = 'S-01' and i.flat.residence.name = 'Sonnenbühl West I'
              and i.round = 0 and p.startsOn = :startsOn and p.endsOn = :endsOn
            """, InspectionEntity.class)
            .setParameter("startsOn", LocalDate.of(2026, 12, 7))
            .setParameter("endsOn", LocalDate.of(2026, 12, 11))
            .getSingleResult();
    }
}
