package de.seezeit.aume.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneId;
import com.jayway.jsonpath.JsonPath;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.notification.InspectionNotificationDeliveryService;
import de.seezeit.aume.notification.InspectionNotificationEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-announcement-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InspectionAnnouncementTests {
    private static final String PATH = "/api/inspection-periods";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ResidenceRepository residences;
    @Autowired private InspectionPeriodRepository periods;
    @Autowired private InspectionNotificationDeliveryService delivery;
    @Autowired private EntityManager entities;

    /** Wie in InspectionPlanningTests: Sonne von den Beispielkontrollen befreien. */
    @BeforeEach
    void freeSonne() {
        entities.createQuery("""
                update InspectionEntity i set i.status = :published
                where i.flat.id in (select f.id from FlatEntity f where f.residence.name = 'Wohnanlage Sonne')
                """)
            .setParameter("published", InspectionEntity.Status.PUBLISHED)
            .executeUpdate();
    }

    @Test
    void draftNamesRecipientsChannelAndPrefillsTheTemplate() throws Exception {
        long id = agreedPeriod();
        mvc.perform(get(PATH + "/" + id + "/announcement-draft").session(login("clerk")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.residenceName").value("Wohnanlage Sonne"))
            .andExpect(jsonPath("$.flatCount").value(2))
            .andExpect(jsonPath("$.channels[0]").value("IN_APP"))
            .andExpect(jsonPath("$.message", containsString("vom %s bis %s".formatted(german(day(10)), german(day(12))))))
            .andExpect(jsonPath("$.message", containsString("zwischen 09:00 und 16:00 Uhr")))
            .andExpect(jsonPath("$.message", containsString("Checkliste")));
    }

    @Test
    void announcingMarksThePeriodAndWritesTheDispatchEntry() throws Exception {
        long id = agreedPeriod();
        announce(id, "\"Eigener Text\"", null, null, periodVersion(id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("ANNOUNCEMENT"))
            .andExpect(jsonPath("$.sentBy.name").value(users.findByUserKey("clerk").orElseThrow().getName()))
            .andExpect(jsonPath("$.residenceName").value("Wohnanlage Sonne"))
            .andExpect(jsonPath("$.flatCount").value(2))
            .andExpect(jsonPath("$.channels[0]").value("IN_APP"))
            .andExpect(jsonPath("$.message").value("Eigener Text"))
            .andExpect(jsonPath("$.attachmentName").isEmpty());

        var period = periods.findById(id).orElseThrow();
        assertThat(period.getAnnouncedAt()).isNotNull();
        assertThat(period.getAnnouncedBy().getUserKey()).isEqualTo("clerk");
        assertThat(period.getRescheduledAt()).isNull();
        mvc.perform(get(PATH).session(login("clerk")))
            .andExpect(jsonPath("$.content[?(@.id == %d)].announcedAt".formatted(id), everyItem(notNullValue())))
            .andExpect(jsonPath("$.content[?(@.id == %d)].rescheduledAt".formatted(id), contains(nullValue())));
        history(id).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void onlyAnAgreedAndNotYetAnnouncedPeriodCanBeAnnounced() throws Exception {
        long id = proposal();
        announce(id, "\"Text\"", null, null, periodVersion(id))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_period_status"));

        respond(id, "agree");
        long seen = periodVersion(id);
        announce(id, "\"Text\"", null, null, seen).andExpect(status().isOk());
        announce(id, "\"Text\"", null, null, periodVersion(id))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_announced"));
        announce(id, "\"Text\"", null, null, seen)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_changed"));
        history(id).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void periodWhoseStartHasPassedCannotBeAnnounced() throws Exception {
        long id = agreedPeriod();
        var period = periods.findById(id).orElseThrow();
        period.setStartsOn(day(0));
        periods.saveAndFlush(period);
        announce(id, "\"Text\"", null, null, periodVersion(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("start_not_in_future"));
        assertThat(periods.findById(id).orElseThrow().getAnnouncedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"null,message_required", "'\"   \"',message_required"})
    void messageIsRequired(String message, String code) throws Exception {
        long id = agreedPeriod();
        announce(id, message, null, null, periodVersion(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void messageMustNotBeTooLong() throws Exception {
        long id = agreedPeriod();
        announce(id, "\"" + "x".repeat(InspectionAnnouncementService.MAX_MESSAGE_LENGTH + 1) + "\"", null, null,
                periodVersion(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("message_too_long"));
    }

    @Test
    void ownUploadCanBeAttachedButNoForeignOrMissingFile() throws Exception {
        long id = agreedPeriod();
        long foreign = upload("student");
        announce(id, "\"Text\"", foreign, "fremd.pdf", periodVersion(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unknown_attachment"));
        announce(id, "\"Text\"", 999_999L, "weg.pdf", periodVersion(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unknown_attachment"));

        long own = upload("clerk");
        announce(id, "\"Text\"", own, "Checkliste.pdf", periodVersion(id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attachmentName").value("Checkliste.pdf"));
    }

    @Test
    void reschedulingSetsTheMarkerAndInformsAllFlatsAutomatically() throws Exception {
        long id = agreedPeriod();
        announce(id, "\"Text\"", null, null, periodVersion(id)).andExpect(status().isOk());
        deliverAll(id);

        reschedule(id, day(20), day(21), "10:00", "14:00", periodVersion(id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("RESCHEDULE"))
            .andExpect(jsonPath("$.startsOn").value(day(20).toString()))
            .andExpect(jsonPath("$.message", containsString("hat sich geändert")))
            .andExpect(jsonPath("$.message", containsString("zwischen 10:00 und 14:00 Uhr")));

        var period = periods.findById(id).orElseThrow();
        assertThat(period.getRescheduledAt()).isAfterOrEqualTo(period.getAnnouncedAt());
        assertThat(period.getStatus()).isEqualTo(InspectionPeriodEntity.Status.AGREED);
        mvc.perform(get(PATH).session(login("clerk")))
            .andExpect(jsonPath("$.content[?(@.id == %d)].rescheduledAt".formatted(id), contains(notNullValue())));
        history(id)
            .andExpect(jsonPath("$[0].kind").value("RESCHEDULE"))
            .andExpect(jsonPath("$[1].kind").value("ANNOUNCEMENT"));

        // Notification-Modul (Team 2) erzeugt aus der neuen Revision eine zweite Ankündigung je WG.
        deliverAll(id);
        var messages = entities.createQuery("""
                select n from InspectionNotificationEntity n
                where n.inspection.period.id = :id and n.kind = :kind order by n.id
                """, InspectionNotificationEntity.class)
            .setParameter("id", id).setParameter("kind", InspectionNotificationEntity.Kind.ANNOUNCEMENT)
            .getResultList();
        assertThat(messages).hasSize(4);
        assertThat(messages.subList(2, 4)).allSatisfy(message -> assertThat(message.getStartsOn()).isEqualTo(day(20)));
    }

    @Test
    void reschedulingNeedsAnAnnouncedPeriodAndARealChange() throws Exception {
        long id = agreedPeriod();
        reschedule(id, day(20), day(21), "09:00", "16:00", periodVersion(id))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_period_status"));
        announce(id, "\"Text\"", null, null, periodVersion(id));
        reschedule(id, day(10), day(12), "09:00", "16:00", periodVersion(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("period_unchanged"));
        reschedule(id, day(0), day(12), "09:00", "16:00", periodVersion(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("start_not_in_future"));
        reschedule(id, day(10), day(12), "16:00", "09:00", periodVersion(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_time_window"));
        reschedule(id, day(10), day(13), "09:00", "16:00", 99)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_changed"));
        assertThat(periods.findById(id).orElseThrow().getRescheduledAt()).isNull();
    }

    @Test
    void foreignPeriodsAreNotAccessible() throws Exception {
        var petershausen = residences.findFirstByName("Petershausen").orElseThrow();
        var foreign = new InspectionPeriodEntity(petershausen, day(10), day(12),
            users.findByUserKey("clerk2").orElseThrow());
        foreign.setStatus(InspectionPeriodEntity.Status.AGREED);
        long id = periods.saveAndFlush(foreign).getId();

        mvc.perform(get(PATH + "/" + id + "/announcement-draft").session(login("clerk"))).andExpect(status().isNotFound());
        announce(id, "\"Text\"", null, null, 0).andExpect(status().isNotFound());
        reschedule(id, day(20), day(21), null, null, 0).andExpect(status().isNotFound());
        history(id).andExpect(status().isNotFound());
        assertThat(periods.findById(id).orElseThrow().getAnnouncedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"student", "caretaker"})
    void onlyTheClerkCanAnnounce(String key) throws Exception {
        long id = agreedPeriod();
        var session = login(key);
        mvc.perform(get(PATH + "/" + id + "/announcement-draft").session(session)).andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/" + id + "/announcements").session(session)).andExpect(status().isForbidden());
        mvc.perform(post(PATH + "/" + id + "/announce").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(announceBody("\"Text\"", null, null, 0)))
            .andExpect(status().isForbidden());
        assertThat(periods.findById(id).orElseThrow().getAnnouncedAt()).isNull();
    }

    @Test
    void anonymousAccessAndMissingCsrfAreRejected() throws Exception {
        long id = agreedPeriod();
        mvc.perform(get(PATH + "/" + id + "/announcement-draft")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH + "/" + id + "/announce").session(login("clerk"))
                .contentType(MediaType.APPLICATION_JSON).content(announceBody("\"Text\"", null, null, periodVersion(id))))
            .andExpect(status().isForbidden());
        assertThat(periods.findById(id).orElseThrow().getAnnouncedAt()).isNull();
    }

    @Test
    void caretakerNoLongerSeesTheAnnouncedPeriodAsRequest() throws Exception {
        long id = agreedPeriod();
        announce(id, "\"Text\"", null, null, periodVersion(id));
        mvc.perform(get(PATH + "/assigned").session(login("caretaker")))
            .andExpect(jsonPath("$.content[*].id", not(org.hamcrest.Matchers.hasItem((int) id))));
    }

    private void deliverAll(long periodId) {
        entities.flush();
        entities.createQuery("select i.id from InspectionEntity i where i.period.id = :id", Long.class)
            .setParameter("id", periodId).getResultList()
            .forEach(inspectionId -> delivery.deliver(inspectionId, InspectionNotificationEntity.Kind.ANNOUNCEMENT));
        entities.flush();
    }

    private long agreedPeriod() throws Exception {
        long id = proposal();
        respond(id, "agree");
        return id;
    }

    private long proposal() throws Exception {
        var content = "{\"residenceId\":%d,\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"windowStart\":\"09:00\",\"windowEnd\":\"16:00\",\"caretakerId\":%d}"
            .formatted(residences.findFirstByName("Wohnanlage Sonne").orElseThrow().getId(), day(10), day(12),
                users.findByUserKey("caretaker").orElseThrow().getId());
        var result = write(post(PATH), "clerk", content).andExpect(status().isCreated());
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void respond(long id, String action) throws Exception {
        write(post(PATH + "/" + id + "/" + action), "caretaker", "{\"version\":" + periodVersion(id) + "}")
            .andExpect(status().isOk());
    }

    private ResultActions announce(long id, String message, Long attachmentId, String attachmentName, long version)
            throws Exception {
        return write(post(PATH + "/" + id + "/announce"), "clerk", announceBody(message, attachmentId, attachmentName, version));
    }

    private static String announceBody(String message, Long attachmentId, String attachmentName, long version) {
        return "{\"message\":%s,\"attachmentId\":%s,\"attachmentName\":%s,\"version\":%d}".formatted(message, attachmentId,
            attachmentName == null ? "null" : "\"" + attachmentName + "\"", version);
    }

    private ResultActions reschedule(long id, LocalDate startsOn, LocalDate endsOn, String windowStart, String windowEnd,
            long version) throws Exception {
        var content = "{\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"windowStart\":%s,\"windowEnd\":%s,\"version\":%d}"
            .formatted(startsOn, endsOn, quoted(windowStart), quoted(windowEnd), version);
        return write(post(PATH + "/" + id + "/reschedule"), "clerk", content);
    }

    private ResultActions history(long id) throws Exception {
        return mvc.perform(get(PATH + "/" + id + "/announcements").session(login("clerk")));
    }

    private long upload(String key) throws Exception {
        var file = new MockMultipartFile("file", "datei.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F'});
        var result = mvc.perform(multipart("/api/attachments").file(file).session(login(key)).with(csrf()))
            .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String key, String content) throws Exception {
        return mvc.perform(request.session(login(key)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private long periodVersion(long id) {
        entities.flush();
        return periods.findById(id).orElseThrow().getVersion();
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String german(LocalDate date) {
        return "%02d.%02d.%d".formatted(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private static LocalDate day(int offset) {
        return LocalDate.now(ZoneId.of("Europe/Berlin")).plusDays(offset);
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
