package de.seezeit.aume.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.seezeit.aume.attachment.AttachmentEntity;
import de.seezeit.aume.attachment.AttachmentService;
import de.seezeit.aume.attachment.FileAttachmentStorage;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.notification.InspectionNotificationEntity.Kind;
import de.seezeit.aume.planning.InspectionAnnouncementEntity;
import de.seezeit.aume.planning.InspectionAnnouncementService;
import de.seezeit.aume.planning.InspectionAnnouncementService.AnnounceRequest;
import de.seezeit.aume.planning.InspectionAnnouncementService.RescheduleRequest;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.StaffAssignmentEntity;
import de.seezeit.aume.residence.StaffKind;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.user.ClerkEntity;
import de.seezeit.aume.user.RoleRepository;
import de.seezeit.aume.user.StudentEntity;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

class StudentNotificationAttachmentTests {
    private static final String INBOX = "/api/student/notifications";
    private static final String DOWNLOAD = INBOX + "/{id}/attachment";
    private static final String PDF = INBOX + "/{id}/checklist.pdf";
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final byte[] ORIGINAL = {0x25, 0x50, 0x44, 0x46, 0, (byte) 0xff};
    private static final String MESSAGE = "Bitte beachtet die Hinweise im Anhang.";
    private static final String NAME = "Hinweise für Küche – 日本語.pdf";

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-notification-attachment;MODE=PostgreSQL")
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    @Transactional
    class WithStorage extends Fixtures {
        private static Path uploads;

        @DynamicPropertySource
        static void storagePath(DynamicPropertyRegistry registry) throws IOException {
            uploads = Files.createTempDirectory("aume-notification-attachment");
            registry.add("aume.storage.location", uploads::toString);
        }

        @AfterAll
        static void cleanStorage() throws IOException {
            if (uploads != null) FileSystemUtils.deleteRecursively(uploads);
        }

        @Autowired private AttachmentService attachments;
        @Autowired private FileAttachmentStorage storage;

        @Test
        void downloadsTheOriginalFileWithSafeHeadersAlongsideTheUnchangedChecklist() throws Exception {
            var file = upload(MediaType.APPLICATION_PDF_VALUE);
            var delivery = deliver(announced(file.id(), NAME), publishedChecklist());
            var reader = asStudent(student);
            var checklistReader = user(student.getUserKey())
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("CHECKLIST_READ"));

            inbox(reader).andExpect(jsonPath("$.content[0].attachment.fileName").value(NAME))
                .andExpect(jsonPath("$.content[0].attachment.id").doesNotExist())
                .andExpect(jsonPath("$.content[0].attachment.storageKey").doesNotExist())
                .andExpect(jsonPath("$.content[0].hasChecklistPdf").value(false));
            mvc.perform(get(PDF, delivery.getId()).with(reader)).andExpect(status().isForbidden());
            inbox(checklistReader).andExpect(jsonPath("$.content[0].attachment.fileName").value(NAME))
                .andExpect(jsonPath("$.content[0].hasChecklistPdf").value(true));
            var pdf = mvc.perform(get(PDF, delivery.getId()).with(checklistReader))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Checkliste_Sauberkeit.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
            assertThat(pdf).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');

            assertUnread(reader);
            var downloaded = mvc.perform(get(DOWNLOAD, delivery.getId()).with(reader))
                .andExpect(status().isOk())
                .andExpect(content().bytes(ORIGINAL))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, Integer.toString(ORIGINAL.length)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
            assertFileName(downloaded, NAME);
            assertUnread(reader);
        }

        @Test
        void sanitizesAnUploadedNameAndUsesASafeFallbackForInvalidMediaMetadata() throws Exception {
            var file = upload("not-a-media-type");
            var delivery = deliver(announced(file.id(), "../privat\\Hinweis\r\n\t\"für Küche\".pdf"), null);
            String safeName = "Hinweis_für Küche_.pdf";
            var reader = asStudent(student);

            inbox(reader).andExpect(jsonPath("$.content[0].attachment.fileName").value(safeName));
            var downloaded = mvc.perform(get(DOWNLOAD, delivery.getId()).with(reader))
                .andExpect(status().isOk()).andExpect(content().bytes(ORIGINAL))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
            assertFileName(downloaded, safeName);
            assertThat(downloaded.andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .doesNotContain("\r", "\n", "privat");
        }

        @Test
        void keepsTheOriginalAttachmentAfterReschedulingAndNeverInheritsIt() throws Exception {
            var original = deliver(announced(upload(MediaType.APPLICATION_PDF_VALUE).id(), NAME), null);
            var revisedEntry = announcements.reschedule(clerk.getUserKey(), period.getId(),
                new RescheduleRequest(today.plusDays(10), today.plusDays(12), null, null, period.getVersion()));
            var revised = deliver(entities.find(InspectionAnnouncementEntity.class, revisedEntry.id()), null);
            var reader = asStudent(student);
            entities.flush();
            entities.clear();

            mvc.perform(get(DOWNLOAD, revised.getId()).with(reader)).andExpect(status().isNotFound());
            assertFileName(mvc.perform(get(DOWNLOAD, original.getId()).with(reader))
                .andExpect(status().isOk()).andExpect(content().bytes(ORIGINAL)), NAME);
            inbox(reader).andExpect(jsonPath("$.content[0].announcementKind").value("RESCHEDULE"))
                .andExpect(jsonPath("$.content[0].attachment").value(nullValue()))
                .andExpect(jsonPath("$.content[1].attachment.fileName").value(NAME));
        }

        @Test
        void deniesAnonymousStaffOtherRecipientsAndUnknownIdsAndKeepsPublicOwnershipChecks() throws Exception {
            var file = upload(MediaType.APPLICATION_PDF_VALUE);
            var delivery = deliver(announced(file.id(), NAME), null);
            var flatmate = student("attachment-flatmate");
            member(flatmate, flat, today);
            var otherDelivery = persist(new NotificationEntity(flatmate, delivery.getInspectionNotification(), "Ankündigung", null));
            var outsider = student("attachment-outsider");
            member(outsider, persist(new FlatEntity(residence, "A-02", 3)), today);
            var staff = user(clerk.getUserKey()).roles("CLERK");
            var reader = asStudent(student);

            mvc.perform(get(DOWNLOAD, delivery.getId())).andExpect(status().isUnauthorized());
            mvc.perform(get(DOWNLOAD, delivery.getId()).with(staff)).andExpect(status().isForbidden());
            mvc.perform(get(DOWNLOAD, delivery.getId()).with(asStudent(flatmate))).andExpect(status().isNotFound());
            mvc.perform(get(DOWNLOAD, otherDelivery.getId()).with(reader)).andExpect(status().isNotFound());
            mvc.perform(get(DOWNLOAD, delivery.getId()).with(asStudent(outsider))).andExpect(status().isNotFound());
            mvc.perform(get(DOWNLOAD, Long.MAX_VALUE).with(reader)).andExpect(status().isNotFound());

            var unrelated = upload(MediaType.TEXT_PLAIN_VALUE);
            mvc.perform(get(DOWNLOAD, delivery.getId()).param("attachmentId", unrelated.id().toString()).with(reader))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
            mvc.perform(get("/api/attachments/{id}", file.id()).with(reader))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
            mvc.perform(get("/api/attachments/{id}", file.id()).with(staff))
                .andExpect(status().isOk()).andExpect(content().bytes(ORIGINAL));
        }

        @Test
        void checksMembershipOnEveryDownloadAndRestoresAccessAfterReturningToTheSameFlat() throws Exception {
            var delivery = deliver(announced(upload(MediaType.APPLICATION_PDF_VALUE).id(), NAME), null);
            var otherFlat = persist(new FlatEntity(residence, "A-02", 3));
            var reader = asStudent(student);

            var concurrent = member(student, otherFlat, today);
            mvc.perform(get(DOWNLOAD, delivery.getId()).with(reader)).andExpect(status().isNotFound());
            concurrent.setValidTo(today.plusDays(1));
            membership.setValidTo(today.plusDays(1));
            onDay(today.plusDays(1));
            mvc.perform(get(DOWNLOAD, delivery.getId()).with(reader)).andExpect(status().isNotFound());

            var switched = member(student, otherFlat, today.plusDays(1));
            mvc.perform(get(DOWNLOAD, delivery.getId()).with(reader)).andExpect(status().isNotFound());
            switched.setValidTo(today.plusDays(2));
            member(student, flat, today.plusDays(2));
            onDay(today.plusDays(2));
            mvc.perform(get(DOWNLOAD, delivery.getId()).with(reader))
                .andExpect(status().isOk()).andExpect(content().bytes(ORIGINAL));
        }

        @Test
        void returnsNotFoundForAMissingFileAndForBothReminderTypes() throws Exception {
            var file = upload(MediaType.APPLICATION_PDF_VALUE);
            var entry = announced(file.id(), NAME);
            var delivery = deliver(entry, null);
            var reader = asStudent(student);
            storage.delete(entities.find(AttachmentEntity.class, file.id()).getStorageKey());

            mvc.perform(get(DOWNLOAD, delivery.getId()).with(reader)).andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

            for (var kind : Set.of(Kind.REMINDER_7_DAYS, Kind.REMINDER_1_DAY)) {
                var reminder = persist(new InspectionNotificationEntity(inspection, kind, entry.getSentAt(),
                    entry.getStartsOn(), entry.getEndsOn(), null, null, null));
                var reminderDelivery = persist(new NotificationEntity(student, reminder, "Erinnerung", null));
                mvc.perform(get(DOWNLOAD, reminderDelivery.getId()).with(reader)).andExpect(status().isNotFound());
            }
            inbox(reader).andExpect(jsonPath("$.content[0].attachment").value(nullValue()))
                .andExpect(jsonPath("$.content[1].attachment").value(nullValue()));
        }

        private AttachmentService.AttachmentView upload(String type) {
            return attachments.store(clerk.getUserKey(), new MockMultipartFile("file", NAME, type, ORIGINAL));
        }
    }

    @Nested
    @SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:aume-notification-attachment-no-storage;MODE=PostgreSQL",
        "KEYCLOAK_URL=https://identity.example.org", "KEYCLOAK_REALM=seezeit", "KEYCLOAK_CLIENT_ID=seezeit-frontend"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Transactional
    class WithoutStorage extends Fixtures {
        @Autowired private ApplicationContext context;
        @MockitoBean private JwtDecoder decoder;

        @Test
        void startsWithoutAnAttachmentAdapterAndRetainsTheMessageAndChecklist() throws Exception {
            assertThat(context.getBeansOfType(AttachmentService.class)).isEmpty();
            assertThat(context.getBeansOfType(FileAttachmentStorage.class)).isEmpty();
            var file = persist(new AttachmentEntity(UUID.randomUUID().toString(), MediaType.APPLICATION_PDF_VALUE,
                ORIGINAL.length, clerk));
            var delivery = deliver(announced(file.getId(), NAME), publishedChecklist());
            var issuer = "https://identity.example.org/realms/seezeit";
            student.linkIdentity(issuer, "attachment-subject");
            when(decoder.decode("attachment-token")).thenReturn(Jwt.withTokenValue("attachment-token")
                .header("alg", "RS256").issuer(issuer).subject("attachment-subject").build());
            RequestPostProcessor reader = request -> {
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer attachment-token");
                return request;
            };

            inbox(reader).andExpect(jsonPath("$.content[0].attachment").value(nullValue()))
                .andExpect(jsonPath("$.content[0].announcementMessage").value(MESSAGE))
                .andExpect(jsonPath("$.content[0].hasChecklistPdf").value(true));
            mvc.perform(get(DOWNLOAD, delivery.getId()).with(reader)).andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
            mvc.perform(get(PDF, delivery.getId()).with(reader)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
            assertUnread(reader);
        }
    }

    abstract static class Fixtures {
        @Autowired MockMvc mvc;
        @Autowired EntityManager entities;
        @Autowired InspectionAnnouncementService announcements;
        @Autowired RoleRepository roles;
        @MockitoBean InspectionNotificationScheduler scheduler;
        @MockitoBean(name = "inspectionNotificationClock") Clock clock;

        LocalDate today;
        ResidenceEntity residence;
        FlatEntity flat;
        ClerkEntity clerk;
        StudentEntity student;
        MembershipEntity membership;
        InspectionPeriodEntity period;
        InspectionEntity inspection;

        @BeforeEach
        void createFixtures() {
            today = LocalDate.now(BERLIN);
            when(clock.getZone()).thenReturn(BERLIN);
            onDay(today);

            residence = persist(new ResidenceEntity("Testwohnanlage Nachrichtenanhang"));
            flat = persist(new FlatEntity(residence, "A-01", 4));
            clerk = persist(new ClerkEntity("attachment-clerk", "Sachbearbeitung", Set.of(), residence));
            persist(new StaffAssignmentEntity(clerk, residence, StaffKind.CLERK));
            student = student("attachment-student");
            membership = member(student, flat, today.minusDays(1));
            period = new InspectionPeriodEntity(residence, today.plusDays(7), today.plusDays(9), clerk);
            period.setStatus(InspectionPeriodEntity.Status.AGREED);
            period.setWindowStart(LocalTime.of(9, 0));
            period.setWindowEnd(LocalTime.of(16, 0));
            persist(period);
            inspection = persist(new InspectionEntity(flat, period));
        }

        void onDay(LocalDate day) {
            when(clock.instant()).thenReturn(day.atStartOfDay(BERLIN).toInstant());
        }

        RequestPostProcessor asStudent(StudentEntity recipient) {
            return user(recipient.getUserKey()).roles("STUDENT");
        }

        StudentEntity student(String key) {
            return persist(new StudentEntity(key, key, Set.of(roles.findByName("STUDENT").orElseThrow()), residence));
        }

        MembershipEntity member(StudentEntity recipient, FlatEntity wg, LocalDate from) {
            return persist(new MembershipEntity(wg, recipient, from));
        }

        InspectionAnnouncementEntity announced(long fileId, String name) {
            var entry = announcements.announce(clerk.getUserKey(), period.getId(),
                new AnnounceRequest(MESSAGE, fileId, name, period.getVersion()));
            return entities.find(InspectionAnnouncementEntity.class, entry.id());
        }

        NotificationEntity deliver(InspectionAnnouncementEntity source, ChecklistVersionEntity checklist) {
            var message = persist(new InspectionNotificationEntity(inspection, Kind.ANNOUNCEMENT, source.getSentAt(),
                source.getStartsOn(), source.getEndsOn(), source.getWindowStart(), source.getWindowEnd(),
                checklist, source, source.getKind()));
            return persist(new NotificationEntity(student, message, "Ankündigung", null));
        }

        ChecklistVersionEntity publishedChecklist() {
            var max = entities.createQuery("select coalesce(max(v.versionNo), 0) from ChecklistVersionEntity v",
                Integer.class).getSingleResult();
            var checklist = new ChecklistVersionEntity(max + 1, clerk);
            checklist.setStatus(ChecklistVersionEntity.Status.PUBLISHED);
            checklist.setPublishedAt(clock.instant());
            persist(checklist);
            persist(new CriterionEntity(checklist, "Küche", "Spüle", "Keine Rückstände.", 1));
            return checklist;
        }

        ResultActions inbox(RequestPostProcessor reader) throws Exception {
            return mvc.perform(get(INBOX).with(reader)).andExpect(status().isOk());
        }

        void assertUnread(RequestPostProcessor reader) throws Exception {
            inbox(reader).andExpect(jsonPath("$.content[0].readAt").value(nullValue()));
            mvc.perform(get(INBOX + "/unread-count").with(reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        }

        <T> T persist(T entity) {
            entities.persist(entity);
            return entity;
        }
    }

    private static void assertFileName(ResultActions downloaded, String expected) {
        var header = downloaded.andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).contains("filename*=UTF-8''");
        var disposition = ContentDisposition.parse(header);
        assertThat(disposition.isAttachment()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo(expected);
    }
}
