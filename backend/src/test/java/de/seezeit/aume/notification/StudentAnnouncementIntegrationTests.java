package de.seezeit.aume.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.inspection.InspectionNotificationSource;
import de.seezeit.aume.planning.InspectionAnnouncementEntity;
import de.seezeit.aume.planning.InspectionAnnouncementEntity.Kind;
import de.seezeit.aume.planning.InspectionPeriodEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.StaffAssignmentEntity;
import de.seezeit.aume.residence.StaffKind;
import de.seezeit.aume.user.CaretakerEntity;
import de.seezeit.aume.user.ClerkEntity;
import de.seezeit.aume.user.RoleRepository;
import de.seezeit.aume.user.StudentEntity;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-student-announcement-integration;MODE=PostgreSQL")
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class StudentAnnouncementIntegrationTests {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String PERIODS = "/api/inspection-periods";
    private static final String INBOX = "/api/student/notifications";
    private static final String ORIGINAL_TEXT = "Liebe WG,\n\nbitte räumt den Bereich unter der Spüle frei.";
    private static Path uploads;

    @DynamicPropertySource
    static void attachmentStorage(DynamicPropertyRegistry registry) throws IOException {
        uploads = Files.createTempDirectory("aume-student-announcement-integration");
        registry.add("aume.storage.location", uploads::toString);
    }

    @AfterAll
    static void cleanStorage() throws IOException {
        if (uploads != null) FileSystemUtils.deleteRecursively(uploads);
    }

    @MockitoBean private InspectionNotificationScheduler automaticScheduler;
    @MockitoBean(name = "inspectionNotificationClock") private Clock clock;
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entities;
    @Autowired private RoleRepository roles;
    @Autowired private AppUserService users;
    @Autowired private InspectionNotificationSource candidates;
    @Autowired private InspectionNotificationDeliveryService delivery;

    private LocalDate today;
    private LocalDate startsOn;
    private ResidenceEntity residence;
    private ClerkEntity clerk;
    private CaretakerEntity caretaker;
    private FlatEntity firstFlat;
    private StudentEntity first;
    private StudentEntity second;
    private StudentEntity outsider;

    @BeforeEach
    void createFixtures() {
        today = LocalDate.now(BERLIN);
        startsOn = today.plusDays(10);
        when(clock.getZone()).thenReturn(BERLIN);
        notificationDay(today);

        residence = persist(new ResidenceEntity("Testanlage für Ankündigungen"));
        clerk = persist(new ClerkEntity("dispatch-clerk", "Sachbearbeitung", Set.of(roles.findByName("CLERK").orElseThrow()),
            residence));
        caretaker = persist(new CaretakerEntity("dispatch-caretaker", "Objektbetreuung",
            Set.of(roles.findByName("CARETAKER").orElseThrow()), residence));
        persist(new StaffAssignmentEntity(clerk, residence, StaffKind.CLERK));
        persist(new StaffAssignmentEntity(caretaker, residence, StaffKind.CARETAKER));
        firstFlat = new FlatEntity(residence, "N-01", 2);
        first = resident("dispatch-first", firstFlat);
        second = resident("dispatch-second", new FlatEntity(residence, "N-02", 2));
        var occupied = new FlatEntity(residence, "N-03", 2);
        outsider = resident("dispatch-outsider", occupied);
        persist(new InspectionEntity(occupied, null));
    }

    @Test
    void deliversAnIndividualAnnouncementAndItsAttachmentOnlyToTheSelectedFlatAcrossRevisions() throws Exception {
        long periodId = id(write(PERIODS, clerk.getUserKey(), """
            {"residenceId": %d, "startsOn": "%s", "endsOn": "%s",
             "windowStart": "09:00", "windowEnd": "16:00", "caretakerId": %d,
             "kind": "INDIVIDUAL", "flatIds": [%d], "occasion": "SAMPLE"}
            """.formatted(residence.getId(), startsOn, startsOn.plusDays(2), caretaker.getId(), firstFlat.getId()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.flatCount").value(1))
            .andExpect(jsonPath("$.flatLabels[0]").value(firstFlat.getLabel())), "$.id");
        write(PERIODS + "/" + periodId + "/agree", caretaker.getUserKey(),
            "{\"version\":" + period(periodId).getVersion() + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AGREED"));

        var fileName = "Hinweise zur Einzelkontrolle.txt";
        byte[] originalBytes = "Hinweise für die ausgewählte WG.".getBytes(StandardCharsets.UTF_8);
        var upload = new MockMultipartFile("file", fileName, MediaType.TEXT_PLAIN_VALUE, originalBytes);
        long attachmentId = id(mvc.perform(multipart("/api/attachments").file(upload).with(as(clerk.getUserKey())).with(csrf()))
            .andExpect(status().isCreated()), "$.id");
        var individualText = "Liebe WG, bitte haltet den Zugang zur Spüle frei.";
        long firstDispatch = id(write(PERIODS + "/" + periodId + "/announce", clerk.getUserKey(), """
            {"message": "%s", "attachmentId": %d, "attachmentName": "%s", "version": %d}
            """.formatted(individualText, attachmentId, fileName, period(periodId).getVersion()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flatCount").value(1)), "$.id");
        scanTwice();

        var initialInbox = inbox(first).andExpect(jsonPath("$.content.length()").value(1));
        assertRevision(initialInbox, 0, dispatch(firstDispatch))
            .andExpect(jsonPath("$.content[0].announcementKind").value("ANNOUNCEMENT"))
            .andExpect(jsonPath("$.content[0].announcementMessage").value(individualText))
            .andExpect(jsonPath("$.content[0].attachment.fileName").value(fileName));
        long firstDelivery = id(initialInbox, "$.content[0].id");
        mvc.perform(get(INBOX + "/{id}/attachment", firstDelivery).with(as(first.getUserKey())))
            .andExpect(status().isOk()).andExpect(content().bytes(originalBytes));
        inbox(second).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get(INBOX + "/{id}/attachment", firstDelivery).with(as(second.getUserKey())))
            .andExpect(status().isNotFound());

        var movedStart = startsOn.plusDays(4);
        long movedDispatch = reschedule(periodId, movedStart, "10:00", "14:00");
        scanTwice();

        var revisedInbox = inbox(first).andExpect(jsonPath("$.content.length()").value(2));
        assertRevision(revisedInbox, 0, dispatch(movedDispatch))
            .andExpect(jsonPath("$.content[0].announcementKind").value("RESCHEDULE"))
            .andExpect(jsonPath("$.content[0].startsOn").value(movedStart.toString()))
            .andExpect(jsonPath("$.content[0].attachment").value(nullValue()));
        assertRevision(revisedInbox, 1, dispatch(firstDispatch))
            .andExpect(jsonPath("$.content[1].id").value((int) firstDelivery))
            .andExpect(jsonPath("$.content[1].announcementMessage").value(individualText))
            .andExpect(jsonPath("$.content[1].attachment.fileName").value(fileName));
        long movedDelivery = id(revisedInbox, "$.content[0].id");
        mvc.perform(get(INBOX + "/{id}/attachment", movedDelivery).with(as(first.getUserKey())))
            .andExpect(status().isNotFound());
        mvc.perform(get(INBOX + "/{id}/attachment", firstDelivery).with(as(first.getUserKey())))
            .andExpect(status().isOk()).andExpect(content().bytes(originalBytes));
        inbox(second).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get(INBOX + "/{id}/attachment", firstDelivery).with(as(second.getUserKey())))
            .andExpect(status().isNotFound());
    }

    @Test
    void deliversOfficialRevisionsToAffectedFlatsAndPreservesEarlierMessages() throws Exception {
        long periodId = planAndAgree();
        long firstDispatch = announce(periodId);
        scanTwice();
        var original = dispatch(firstDispatch);
        assertThat(original.getMessage()).isEqualTo(ORIGINAL_TEXT);
        assertThat(original.getSentAt()).isEqualTo(period(periodId).getAnnouncedAt());
        var initialInbox = inbox(first).andExpect(jsonPath("$.content.length()").value(1));
        assertRevision(initialInbox, 0, original);
        assertRevision(inbox(second), 0, original).andExpect(jsonPath("$.content.length()").value(1));
        inbox(outsider).andExpect(jsonPath("$.content.length()").value(0));

        long firstDelivery = id(initialInbox, "$.content[0].id");
        mvc.perform(put(INBOX + "/{id}/read", firstDelivery).with(as(second.getUserKey())).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(put(INBOX + "/{id}/read", firstDelivery).with(as(first.getUserKey())).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get(INBOX).with(as(clerk.getUserKey()))).andExpect(status().isForbidden());
        mvc.perform(get(INBOX)).andExpect(status().isUnauthorized());

        var movedStart = startsOn.plusDays(4);
        long dateDispatch = reschedule(periodId, movedStart, "09:00", "16:00");
        scanTwice();
        long timeDispatch = reschedule(periodId, movedStart, "10:00", "14:00");
        scanTwice();

        var dateChange = dispatch(dateDispatch);
        var timeChange = dispatch(timeDispatch);
        assertThat(dateChange.getKind()).isEqualTo(Kind.RESCHEDULE);
        assertThat(timeChange.getKind()).isEqualTo(Kind.RESCHEDULE);
        assertThat(timeChange.getSentAt()).isEqualTo(period(periodId).getRescheduledAt());
        assertThat(dateChange.getStartsOn()).isEqualTo(movedStart);
        assertThat(timeChange.getStartsOn()).isEqualTo(dateChange.getStartsOn());
        assertThat(timeChange.getEndsOn()).isEqualTo(dateChange.getEndsOn());
        assertThat(dateChange.getWindowStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(timeChange.getWindowStart()).isEqualTo(LocalTime.of(10, 0));
        assertThat(timeChange.getMessage()).contains("zwischen 10:00 und 14:00 Uhr");

        var firstInbox = inbox(first).andExpect(jsonPath("$.content.length()").value(3));
        assertRevision(firstInbox, 0, timeChange);
        assertRevision(firstInbox, 1, dateChange);
        assertRevision(firstInbox, 2, dispatch(firstDispatch))
            .andExpect(jsonPath("$.content[2].id").value((int) firstDelivery))
            .andExpect(jsonPath("$.content[2].readAt").value(today.atStartOfDay(BERLIN).toInstant().toString()));
        var secondInbox = inbox(second).andExpect(jsonPath("$.content.length()").value(3));
        assertRevision(secondInbox, 0, timeChange);
        assertRevision(secondInbox, 1, dateChange);
        assertRevision(secondInbox, 2, dispatch(firstDispatch))
            .andExpect(jsonPath("$.content[2].readAt").value(nullValue()));
        inbox(outsider).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get(INBOX + "/unread-count").with(as(first.getUserKey())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void selectsOnlyTheDispatchMatchingPeriodTimestampAndKind() throws Exception {
        long periodId = planAndAgree();
        var original = dispatch(announce(periodId));
        var otherPeriod = persist(new InspectionPeriodEntity(residence, startsOn, startsOn.plusDays(2), clerk));
        copyDispatch(original, otherPeriod.getId(), original.getSentAt(), Kind.ANNOUNCEMENT);
        copyDispatch(original, periodId, original.getSentAt().plusSeconds(1), Kind.ANNOUNCEMENT);
        copyDispatch(original, periodId, original.getSentAt(), Kind.RESCHEDULE);

        scanTwice();

        assertRevision(inbox(first), 0, dispatch(original.getId()));
        assertThat(storedSourceIds(periodId)).containsExactly(original.getId(), original.getId());
    }

    @Test
    void deliversWithTheFallbackWhenTwoDispatchesMatchTheSameRevision() throws Exception {
        long periodId = planAndAgree();
        var original = dispatch(announce(periodId));
        copyDispatch(original, periodId, original.getSentAt(), Kind.ANNOUNCEMENT);

        scanTwice();

        inbox(first).andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].announcementKind").value("ANNOUNCEMENT"))
            .andExpect(jsonPath("$.content[0].announcementMessage").value(nullValue()));
        assertThat(storedSourceIds(periodId)).hasSize(2).allSatisfy(sourceId -> assertThat(sourceId).isNull());
    }

    @Test
    void leavesAnExistingDeliveryUnchangedEvenWhenAnOfficialDispatchMatches() throws Exception {
        long periodId = planAndAgree();
        var original = dispatch(announce(periodId));
        var inspection = entities.createQuery("""
            select i from InspectionEntity i
            where i.period.id = :periodId and i.flat.label = 'N-01'
            """, InspectionEntity.class).setParameter("periodId", periodId).getSingleResult();
        var legacy = persist(new InspectionNotificationEntity(inspection, InspectionNotificationEntity.Kind.ANNOUNCEMENT,
            original.getSentAt(), startsOn, startsOn.plusDays(2), LocalTime.of(9, 0), LocalTime.of(16, 0), null));
        var deliveryId = persist(new NotificationEntity(first, legacy, "Ankündigung", null)).getId();

        scanTwice();

        inbox(first).andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(deliveryId.intValue()))
            .andExpect(jsonPath("$.content[0].announcementKind").value(nullValue()))
            .andExpect(jsonPath("$.content[0].announcementMessage").value(nullValue()));
        assertRevision(inbox(second), 0, dispatch(original.getId()));
        assertThat(entities.find(InspectionNotificationEntity.class, legacy.getId()).getSourceAnnouncement()).isNull();
    }

    @Test
    void deliversBothRemindersForTheNewPeriodWithoutReusingItsDispatchText() throws Exception {
        long periodId = planAndAgree();
        announce(periodId);
        var movedStart = startsOn.plusDays(4);
        reschedule(periodId, movedStart, "10:00", "14:00");

        notificationDay(startsOn.minusDays(7));
        scanTwice();
        inbox(first).andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].type").value("ANNOUNCEMENT"));

        notificationDay(movedStart.minusDays(7));
        scanTwice();
        notificationDay(movedStart.minusDays(1));
        scanTwice();

        var result = inbox(first).andExpect(jsonPath("$.content.length()").value(3));
        assertReminder(result, 0, "REMINDER_1_DAY", movedStart);
        assertReminder(result, 1, "REMINDER_7_DAYS", movedStart);
        inbox(outsider).andExpect(jsonPath("$.content.length()").value(0));
    }

    private long planAndAgree() throws Exception {
        long id = id(write(PERIODS, clerk.getUserKey(), """
            {"residenceId": %d, "startsOn": "%s", "endsOn": "%s",
             "windowStart": "09:00", "windowEnd": "16:00", "caretakerId": %d}
            """.formatted(residence.getId(), startsOn, startsOn.plusDays(2), caretaker.getId()))
            .andExpect(status().isCreated()), "$.id");
        write(PERIODS + "/" + id + "/agree", caretaker.getUserKey(), "{\"version\":" + period(id).getVersion() + "}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.flatCount").value(2));
        return id;
    }

    private long announce(long periodId) throws Exception {
        return id(write(PERIODS + "/" + periodId + "/announce", clerk.getUserKey(), """
            {"message": "Liebe WG,\\n\\nbitte räumt den Bereich unter der Spüle frei.", "version": %d}
            """.formatted(period(periodId).getVersion())).andExpect(status().isOk()), "$.id");
    }

    private long reschedule(long periodId, LocalDate start, String from, String to) throws Exception {
        return id(write(PERIODS + "/" + periodId + "/reschedule", clerk.getUserKey(), """
            {"startsOn": "%s", "endsOn": "%s", "windowStart": "%s", "windowEnd": "%s", "version": %d}
            """.formatted(start, start.plusDays(2), from, to, period(periodId).getVersion()))
            .andExpect(status().isOk()), "$.id");
    }

    private void scanTwice() {
        entities.flush();
        entities.clear();
        var scan = new InspectionNotificationScheduler(candidates, delivery, clock);
        scan.run();
        scan.run();
        entities.flush();
        entities.clear();
    }

    private void notificationDay(LocalDate day) {
        when(clock.instant()).thenReturn(day.atStartOfDay(BERLIN).toInstant());
    }

    private InspectionPeriodEntity period(long id) {
        return entities.find(InspectionPeriodEntity.class, id);
    }

    private InspectionAnnouncementEntity dispatch(long id) {
        return entities.find(InspectionAnnouncementEntity.class, id);
    }

    private void copyDispatch(InspectionAnnouncementEntity original, long periodId, Instant sentAt, Kind kind) {
        entities.createNativeQuery("""
            insert into inspection_announcements
                (period_id, sent_at, kind, sent_by, residence_id, flat_count, channels, message,
                 starts_on, ends_on, window_start, window_end)
            select :periodId, :sentAt, :kind, sent_by, residence_id, flat_count, channels, 'Anderer Versandeintrag',
                starts_on, ends_on, window_start, window_end
            from inspection_announcements where id = :id
            """).setParameter("periodId", periodId).setParameter("sentAt", sentAt).setParameter("kind", kind.name())
            .setParameter("id", original.getId()).executeUpdate();
    }

    private List<Long> storedSourceIds(long periodId) {
        return entities.createQuery("""
            select dispatch.id from InspectionNotificationEntity n left join n.sourceAnnouncement dispatch
            where n.inspection.period.id = :periodId
            """, Long.class).setParameter("periodId", periodId).getResultList();
    }

    private ResultActions inbox(StudentEntity student) throws Exception {
        return mvc.perform(get(INBOX).with(as(student.getUserKey())))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    private ResultActions write(String path, String userKey, String body) throws Exception {
        return mvc.perform(post(path).with(as(userKey)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private RequestPostProcessor as(String userKey) {
        var user = users.requireUser(userKey);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(userKey, null, users.authorities(user)));
    }

    private static long id(ResultActions response, String path) throws Exception {
        Number id = JsonPath.read(response.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), path);
        return id.longValue();
    }

    private static ResultActions assertRevision(ResultActions result, int index, InspectionAnnouncementEntity entry)
            throws Exception {
        var field = "$.content[" + index + "].";
        return result.andExpect(jsonPath(field + "type").value("ANNOUNCEMENT"))
            .andExpect(jsonPath(field + "occurredAt").value(entry.getSentAt().toString()))
            .andExpect(jsonPath(field + "startsOn").value(entry.getStartsOn().toString()))
            .andExpect(jsonPath(field + "endsOn").value(entry.getEndsOn().toString()))
            .andExpect(jsonPath(field + "windowStart").value(entry.getWindowStart().toString()))
            .andExpect(jsonPath(field + "windowEnd").value(entry.getWindowEnd().toString()))
            .andExpect(jsonPath(field + "announcementKind").value(entry.getKind().name()))
            .andExpect(jsonPath(field + "announcementMessage").value(entry.getMessage()));
    }

    private static void assertReminder(ResultActions result, int index, String type, LocalDate start) throws Exception {
        var field = "$.content[" + index + "].";
        result.andExpect(jsonPath(field + "type").value(type))
            .andExpect(jsonPath(field + "startsOn").value(start.toString()))
            .andExpect(jsonPath(field + "hasChecklistPdf").value(false))
            .andExpect(jsonPath(field + "announcementKind").value(nullValue()))
            .andExpect(jsonPath(field + "announcementMessage").value(nullValue()));
    }

    private StudentEntity resident(String key, FlatEntity flat) {
        persist(flat);
        var student = persist(new StudentEntity(key, key, Set.of(roles.findByName("STUDENT").orElseThrow()), residence));
        persist(new MembershipEntity(flat, student, today.minusDays(1)));
        return student;
    }

    private <T> T persist(T entity) {
        entities.persist(entity);
        return entity;
    }
}
