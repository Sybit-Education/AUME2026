package de.seezeit.aume.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.ChecklistVersionEntity.Status;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.standard.CriterionTranslationEntity;
import de.seezeit.aume.user.ClerkEntity;
import de.seezeit.aume.user.StudentEntity;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-student-notification-pdf;MODE=PostgreSQL")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class StudentNotificationPdfTests {
    private static final String PATH = "/api/student/notifications";
    private static final Instant NOW = Instant.parse("2040-12-03T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2040, 12, 3);

    @MockitoBean private InspectionNotificationScheduler scheduler;
    @MockitoBean(name = "inspectionNotificationClock") private Clock clock;
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entities;

    private ResidenceEntity residence;
    private FlatEntity flat;
    private ClerkEntity clerk;
    private StudentEntity student;
    private MembershipEntity membership;
    private InspectionEntity inspection;
    private MockHttpSession reader;

    @BeforeEach
    void fixture() {
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        when(clock.instant()).thenReturn(NOW);
        residence = persist(new ResidenceEntity("PDF Testanlage"));
        flat = persist(new FlatEntity(residence, "PDF-01", 4));
        clerk = persist(new ClerkEntity("pdf-clerk", "Sachbearbeitung", Set.of(), residence));
        student = student("pdf-student");
        membership = persist(new MembershipEntity(flat, student, TODAY));
        var period = persist(new InspectionPeriodEntity(residence, TODAY.plusDays(7), TODAY.plusDays(9), clerk));
        period.setAnnouncedAt(NOW);
        inspection = persist(new InspectionEntity(flat, period));
        reader = session(student.getUserKey(), "ROLE_STUDENT", "CHECKLIST_READ");
    }

    @Test
    void rendersTheReferencedRetiredVersionWithItsOwnTranslationsAndKeepsTheDeliveryUnread() throws Exception {
        var original = version(701, Status.PUBLISHED);
        var surface = persist(new CriterionEntity(original, "Küche", "Arbeitsflächen", "Frei von Rückständen.", 1));
        persist(new CriterionTranslationEntity(surface, "en", "Kitchen", "Work surfaces", "Free of residue."));
        persist(new CriterionEntity(original, "Bad", "Dusche", "Abfluss prüfen.", 2));
        var delivery = deliver(student, Kind.ANNOUNCEMENT, original);

        var replacement = version(702, Status.PUBLISHED);
        persist(new CriterionEntity(replacement, "Flur", "Neue Fassung", "Aktueller Text.", 1));
        original.setStatus(Status.RETIRED);

        assertAvailability(reader, delivery.getId(), true);
        var german = body(download(reader, delivery.getId(), "de")
            .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "de"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Checkliste_Sauberkeit.pdf\"")));
        assertThat(text(german)).contains("Sauberkeitscheckliste", "Fassung 701", "Küche",
            "Arbeitsflächen", "Frei von Rückständen.", "Bad", "Dusche", "Abfluss prüfen.")
            .doesNotContain("Neue Fassung");
        var english = body(download(reader, delivery.getId(), "en")
            .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "en"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Cleanliness_Checklist.pdf\"")));
        assertThat(text(english)).contains("Cleanliness checklist", "Version 701", "Kitchen",
            "Work surfaces", "Free of residue.", "Bad", "Dusche", "Abfluss prüfen.")
            .doesNotContain("Arbeitsflächen", "Neue Fassung");

        mvc.perform(get("/api/checklist").session(reader)).andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(702));
        mvc.perform(get(PATH).session(reader)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].readAt").value(nullValue()));
        mvc.perform(get(PATH + "/unread-count").session(reader)).andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void wrapsALongChecklistAcrossA4PortraitPagesInCriterionOrder() throws Exception {
        var checklist = version(701, Status.PUBLISHED);
        for (int position = 30; position >= 1; position--) {
            persist(new CriterionEntity(checklist, "Küche", "Prüfpunkt " + position,
                "Dieser Text beschreibt das Prüfkriterium ausführlich und erscheint direkt unter der Überschrift. ".repeat(4),
                position));
        }
        var delivery = deliver(student, Kind.ANNOUNCEMENT, checklist);
        var pdf = body(download(reader, delivery.getId(), "fr"));
        try (var pages = new PdfReader(pdf)) {
            assertThat(pages.getNumberOfPages()).isGreaterThan(1);
            assertThat(pages.getPageSize(1).getWidth()).isEqualTo(PageSize.A4.getWidth());
            assertThat(pages.getPageSize(1).getHeight()).isEqualTo(PageSize.A4.getHeight());
        }
        assertThat(text(pdf)).contains("Sauberkeitscheckliste", "Küche")
            .containsPattern("(?s)Prüfpunkt 1\\b.*Prüfpunkt 30\\b");
    }

    @Test
    void advertisesNoPdfForRemindersAnAnnouncementWithoutVersionOrADraft() throws Exception {
        var noVersion = deliver(student, Kind.ANNOUNCEMENT, null);
        var sevenDays = deliver(student, Kind.REMINDER_7_DAYS, null);
        var oneDay = deliver(student, Kind.REMINDER_1_DAY, null);
        // The common message key differs by revision. This fixture exercises a defensive download check.
        var draftMessage = persist(new InspectionNotificationEntity(inspection, Kind.ANNOUNCEMENT, NOW.plusSeconds(1),
            TODAY.plusDays(7), TODAY.plusDays(9), null, null, version(701, Status.DRAFT)));
        var draft = persist(new NotificationEntity(student, draftMessage, "Ankündigung", null));

        for (var delivery : List.of(noVersion, sevenDays, oneDay, draft)) {
            assertAvailability(reader, delivery.getId(), false);
            mvc.perform(get(pdfPath(delivery.getId())).session(reader)).andExpect(status().isNotFound());
        }
    }

    @Test
    void checksRolePermissionRecipientAndCurrentFlatOnEveryDownload() throws Exception {
        var checklist = version(701, Status.PUBLISHED);
        var delivery = deliver(student, Kind.ANNOUNCEMENT, checklist);
        var flatmate = student("pdf-flatmate");
        persist(new MembershipEntity(flat, flatmate, TODAY));
        var withoutChecklist = session(student.getUserKey(), "ROLE_STUDENT");

        mvc.perform(get(pdfPath(delivery.getId()))).andExpect(status().isUnauthorized());
        mvc.perform(get(pdfPath(delivery.getId())).session(session(clerk.getUserKey(), "ROLE_CLERK", "CHECKLIST_READ")))
            .andExpect(status().isForbidden());
        mvc.perform(get(pdfPath(delivery.getId())).session(withoutChecklist)).andExpect(status().isForbidden());
        assertAvailability(withoutChecklist, delivery.getId(), false);
        mvc.perform(get(pdfPath(delivery.getId())).session(session(flatmate.getUserKey(), "ROLE_STUDENT", "CHECKLIST_READ")))
            .andExpect(status().isNotFound());
        mvc.perform(get(pdfPath(Long.MAX_VALUE)).session(reader)).andExpect(status().isNotFound());

        var otherFlat = persist(new FlatEntity(residence, "PDF-02", 2));
        var otherMembership = persist(new MembershipEntity(otherFlat, student, TODAY));
        mvc.perform(get(pdfPath(delivery.getId())).session(reader)).andExpect(status().isNotFound());
        otherMembership.setValidTo(TODAY);
        membership.setValidTo(TODAY);
        mvc.perform(get(pdfPath(delivery.getId())).session(reader)).andExpect(status().isNotFound());
    }

    private void assertAvailability(MockHttpSession session, long id, boolean expected) throws Exception {
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.id == " + id + ")].hasChecklistPdf").value(contains(expected)));
    }

    private ResultActions download(MockHttpSession session, long id, String language) throws Exception {
        return mvc.perform(get(pdfPath(id)).session(session).header(HttpHeaders.ACCEPT_LANGUAGE, language))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    private static String pdfPath(long id) {
        return PATH + "/" + id + "/checklist.pdf";
    }

    private static byte[] body(ResultActions result) {
        return result.andReturn().getResponse().getContentAsByteArray();
    }

    private static String text(byte[] pdf) throws Exception {
        try (var reader = new PdfReader(pdf)) {
            var extractor = new PdfTextExtractor(reader);
            var text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        }
    }

    private ChecklistVersionEntity version(int number, Status status) {
        var version = new ChecklistVersionEntity(number, clerk);
        version.setStatus(status);
        if (status == Status.PUBLISHED) version.setPublishedAt(NOW);
        return persist(version);
    }

    private NotificationEntity deliver(StudentEntity recipient, Kind kind, ChecklistVersionEntity version) {
        var message = persist(new InspectionNotificationEntity(inspection, kind, NOW,
            TODAY.plusDays(7), TODAY.plusDays(9), null, null, version));
        return persist(new NotificationEntity(recipient, message, "Kontrollnachricht", null));
    }

    private StudentEntity student(String key) {
        return persist(new StudentEntity(key, key, Set.of(), residence));
    }

    private <T> T persist(T entity) {
        entities.persist(entity);
        return entity;
    }

    private static MockHttpSession session(String key, String... authorities) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(key, null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
