package de.seezeit.aume.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import com.jayway.jsonpath.JsonPath;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffAssignmentEntity;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.residence.StaffKind;
import de.seezeit.aume.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-planning-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InspectionPlanningTests {
    private static final String PATH = "/api/inspection-periods";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private InspectionPeriodRepository periods;
    @Autowired private StaffAssignmentRepository staff;
    @Autowired private EntityManager entities;

    @Test
    void optionsShowOwnResidencesWithFlatCountAndDefaultCaretaker() throws Exception {
        mvc.perform(get(PATH + "/options").session(login("clerk")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.residences[*].name").value(contains("Egg", "Wohnanlage Sonne")))
            .andExpect(jsonPath("$.residences[1].flatCount").value(2))
            .andExpect(jsonPath("$.residences[1].caretakerId").value(userId("caretaker").intValue()))
            .andExpect(jsonPath("$.residences[1].flatsWithOpenInspection").isEmpty())
            .andExpect(jsonPath("$.residences[0].flatsWithOpenInspection").value(contains("E-12", "E-14")));
    }

    @Test
    void proposalCoversAllFlatsAndWaitsForTheCaretaker() throws Exception {
        var created = propose(sonne(), day(10), day(12), userId("caretaker"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("WAITING_FOR_CARETAKER"))
            .andExpect(jsonPath("$.kind").value("REGULAR"))
            .andExpect(jsonPath("$.flatCount").value(2));
        long id = id(created);

        mvc.perform(get(PATH + "/assigned").session(login("caretaker")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value((int) id))
            .andExpect(jsonPath("$.content[0].residenceName").value("Wohnanlage Sonne"));
    }

    @ParameterizedTest
    @CsvSource({
        "'',12,dates_required",
        "10,'',dates_required",
        "12,10,end_before_start",
        "0,3,start_not_in_future",
        "-2,3,start_not_in_future",
    })
    void invalidDatesNameTheReason(String start, String end, String code) throws Exception {
        var before = periods.count();
        var startsOn = start.isEmpty() ? null : day(Integer.parseInt(start));
        var endsOn = end.isEmpty() ? null : day(Integer.parseInt(end));
        propose(sonne(), startsOn, endsOn, userId("caretaker"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(code));
        assertThat(periods.count()).isEqualTo(before);
    }

    @Test
    void timeWindowIsOptionalButMustBeValid() throws Exception {
        write(post(PATH), "clerk", body(sonne(), day(10), day(12), null, null, userId("caretaker")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.windowStart").isEmpty());
        for (var window : new String[][]{{"16:00", "09:00"}, {"09:00", null}}) {
            write(post(PATH), "clerk", body(sonne(), day(10), day(12), window[0], window[1], userId("caretaker")))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void caretakerCanBeChangedForADeputyButMustExist() throws Exception {
        long id = id(propose(sonne(), day(10), day(12), userId("caretaker2"))
            .andExpect(jsonPath("$.caretaker.id").value(userId("caretaker2").intValue())));
        respond(id, "agree", "caretaker", "{\"version\":0}").andExpect(status().isNotFound());
        propose(sonne(), day(10), day(12), userId("clerk"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unknown_caretaker"));
        propose(sonne(), day(10), day(12), null)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("caretaker_required"));
    }

    @Test
    void flatsWithOpenInspectionAreHintedAndGetNoSecondInspection() throws Exception {
        var flat = flats.findByResidenceIdAndLabel(sonne(), "S-01").orElseThrow();
        entities.persist(new InspectionEntity(flat, null));
        entities.flush();

        mvc.perform(get(PATH + "/options").session(login("clerk")))
            .andExpect(jsonPath("$.residences[1].flatsWithOpenInspection").value(contains("S-01")));
        propose(sonne(), day(10), day(12), userId("caretaker"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.flatCount").value(1));
        propose(sonne(), day(20), day(22), userId("caretaker"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("no_flats_available"));
    }

    @Test
    void agreementMovesToAgreed() throws Exception {
        long id = proposal();
        respond(id, "agree", "caretaker", version(id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AGREED"));
        respond(id, "agree", "caretaker", version(id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("invalid_period_status"));
    }

    @Test
    void acceptedCounterProposalTakesOverThePeriod() throws Exception {
        long id = proposal();
        respond(id, "counter", "caretaker", counter(day(20), day(22), "Urlaub", periodVersion(id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COUNTER_PROPOSED"))
            .andExpect(jsonPath("$.startsOn").value(day(10).toString()))
            .andExpect(jsonPath("$.counter.startsOn").value(day(20).toString()))
            .andExpect(jsonPath("$.counter.reason").value("Urlaub"));

        write(post(PATH + "/" + id + "/accept-counter"), "clerk", version(id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AGREED"))
            .andExpect(jsonPath("$.startsOn").value(day(20).toString()))
            .andExpect(jsonPath("$.endsOn").value(day(22).toString()))
            .andExpect(jsonPath("$.windowStart").value("09:00:00"))
            .andExpect(jsonPath("$.counter").isEmpty());
    }

    @Test
    void acceptedCounterProposalTakesOverItsTimeWindow() throws Exception {
        long id = proposal();
        respond(id, "counter", "caretaker", counterWithWindow(day(20), day(22), "\"10:00\"", "\"14:00\"", periodVersion(id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowStart").value("09:00:00"))
            .andExpect(jsonPath("$.counter.windowStart").value("10:00:00"))
            .andExpect(jsonPath("$.counter.windowEnd").value("14:00:00"));
        write(post(PATH + "/" + id + "/accept-counter"), "clerk", version(id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowStart").value("10:00:00"))
            .andExpect(jsonPath("$.windowEnd").value("14:00:00"));
    }

    @Test
    void counterProposalNeedsAReasonAndValidDates() throws Exception {
        long id = proposal();
        respond(id, "counter", "caretaker", counter(day(20), day(22), "  ", periodVersion(id)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("reason_required"));
        respond(id, "counter", "caretaker", counter(day(22), day(20), "Urlaub", periodVersion(id)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("end_before_start"));
        respond(id, "counter", "caretaker", counterWithWindow(day(20), day(22), "\"14:00\"", "\"10:00\"", periodVersion(id)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_time_window"));
        respond(id, "counter", "caretaker", counterWithWindow(day(20), day(22), "\"10:00\"", "null", periodVersion(id)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_time_window"));
    }

    @Test
    void clerkCanAnswerACounterProposalWithANewPeriod() throws Exception {
        long id = proposal();
        respond(id, "counter", "caretaker", counter(day(20), day(22), "Urlaub", periodVersion(id)));
        update(id, day(25), day(27))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WAITING_FOR_CARETAKER"))
            .andExpect(jsonPath("$.startsOn").value(day(25).toString()))
            .andExpect(jsonPath("$.counter").isEmpty());
    }

    @Test
    void changingAnAgreedPeriodNeedsANewAgreement() throws Exception {
        long id = proposal();
        respond(id, "agree", "caretaker", version(id));
        update(id, day(10), day(12)).andExpect(jsonPath("$.status").value("AGREED"));
        update(id, day(11), day(13)).andExpect(jsonPath("$.status").value("WAITING_FOR_CARETAKER"));
    }

    @Test
    void announcedPeriodsCanNoLongerBeChanged() throws Exception {
        long id = proposal();
        var period = periods.findById(id).orElseThrow();
        period.setAnnouncedAt(Instant.now());
        periods.saveAndFlush(period);
        update(id, day(11), day(13))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_announced"));
        respond(id, "agree", "caretaker", version(id))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_announced"));
    }

    @Test
    void caretakerCannotAgreeToAPeriodThatChangedInTheMeantime() throws Exception {
        long id = proposal();
        var seen = version(id);
        update(id, day(15), day(17)).andExpect(status().isOk());
        respond(id, "agree", "caretaker", seen)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_changed"));
    }

    @Test
    void foreignResidencesAndPeriodsAreNotAccessible() throws Exception {
        var petershausen = residences.findFirstByName("Petershausen").orElseThrow().getId();
        propose(petershausen, day(10), day(12), userId("caretaker2")).andExpect(status().isNotFound());

        var foreign = new InspectionPeriodEntity(residences.findById(petershausen).orElseThrow(), day(10), day(12),
            users.findByUserKey("clerk2").orElseThrow());
        foreign.setCaretaker(users.findByUserKey("caretaker2").orElseThrow());
        long id = periods.saveAndFlush(foreign).getId();
        update(id, day(11), day(13)).andExpect(status().isNotFound());
        write(post(PATH + "/" + id + "/accept-counter"), "clerk", "{\"version\":0}").andExpect(status().isNotFound());
        respond(id, "agree", "caretaker", "{\"version\":0}").andExpect(status().isNotFound());
        mvc.perform(get(PATH).session(login("clerk")).param("size", "100"))
            .andExpect(jsonPath("$.content[*].id", not(hasItem((int) id))));
    }

    @ParameterizedTest
    @CsvSource({"student", "caretaker"})
    void onlyTheClerkCanPlan(String key) throws Exception {
        var session = login(key);
        mvc.perform(get(PATH + "/options").session(session)).andExpect(status().isForbidden());
        mvc.perform(get(PATH).session(session)).andExpect(status().isForbidden());
        mvc.perform(post(PATH).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(sonne(), day(10), day(12), "09:00", "16:00", userId("caretaker"))))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"student", "clerk"})
    void onlyTheCaretakerCanRespond(String key) throws Exception {
        long id = proposal();
        var session = login(key);
        mvc.perform(get(PATH + "/assigned").session(session)).andExpect(status().isForbidden());
        for (var action : new String[]{"agree", "counter"}) {
            mvc.perform(post(PATH + "/" + id + "/" + action).session(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(counter(day(20), day(22), "x", 0)))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousAccessAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).session(login("clerk")).contentType(MediaType.APPLICATION_JSON)
                .content(body(sonne(), day(10), day(12), "09:00", "16:00", userId("caretaker"))))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"PLANNED,1,1", "DRAFT,1,1", "FINALIZED,1,1", "PUBLISHED,0,2"})
    void onlyUnpublishedInspectionsCountAsOpen(InspectionEntity.Status status, int hinted, int planned) throws Exception {
        var inspection = new InspectionEntity(flats.findByResidenceIdAndLabel(sonne(), "S-01").orElseThrow(), null);
        inspection.setStatus(status);
        entities.persist(inspection);
        entities.flush();

        mvc.perform(get(PATH + "/options").session(login("clerk")))
            .andExpect(jsonPath("$.residences[1].flatsWithOpenInspection.length()").value(hinted));
        propose(sonne(), day(10), day(12), userId("caretaker"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.flatCount").value(planned));
    }

    @Test
    void residenceWithoutFlatsCannotBePlannedAndHasNoDefaultCaretaker() throws Exception {
        var empty = residences.save(new ResidenceEntity("Leer"));
        staff.save(new StaffAssignmentEntity(users.findByUserKey("clerk").orElseThrow(), empty, StaffKind.CLERK));

        mvc.perform(get(PATH + "/options").session(login("clerk")))
            .andExpect(jsonPath("$.residences[1].name").value("Leer"))
            .andExpect(jsonPath("$.residences[1].flatCount").value(0))
            .andExpect(jsonPath("$.residences[1].caretakerId").isEmpty());
        propose(empty.getId(), day(10), day(12), userId("caretaker"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("no_flats_available"));
    }

    @Test
    void changingTheCaretakerOfAnAgreedPeriodNeedsAgreementFromTheNewPerson() throws Exception {
        long id = proposal();
        respond(id, "agree", "caretaker", version(id));

        write(put(PATH + "/" + id), "clerk", updateBody(day(10), day(12), "09:00", "16:00", userId("caretaker2"), periodVersion(id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WAITING_FOR_CARETAKER"))
            .andExpect(jsonPath("$.caretaker.id").value(userId("caretaker2").intValue()));
        mvc.perform(get(PATH + "/assigned").session(login("caretaker")))
            .andExpect(jsonPath("$.content[*].id", not(hasItem((int) id))));
        respond(id, "agree", "caretaker", version(id)).andExpect(status().isNotFound());
    }

    @Test
    void clerkCannotOverwriteAPeriodThatChangedInTheMeantime() throws Exception {
        long id = proposal();
        long seen = periodVersion(id);
        respond(id, "agree", "caretaker", version(id)).andExpect(status().isOk());
        write(put(PATH + "/" + id), "clerk", updateBody(day(15), day(17), "09:00", "16:00", userId("caretaker"), seen))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_changed"));
    }

    @Test
    void counterProposalWhoseStartHasPassedCannotBeAccepted() throws Exception {
        long id = proposal();
        respond(id, "counter", "caretaker", counter(day(20), day(22), "Urlaub", periodVersion(id)));
        var period = periods.findById(id).orElseThrow();
        period.setCounterStartsOn(day(0));
        periods.saveAndFlush(period);

        write(post(PATH + "/" + id + "/accept-counter"), "clerk", version(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("start_not_in_future"));
        var unchanged = periods.findById(id).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(InspectionPeriodEntity.Status.COUNTER_PROPOSED);
        assertThat(unchanged.getStartsOn()).isEqualTo(day(10));
    }

    @Test
    void waitingPeriodHasNoCounterProposalToAccept() throws Exception {
        long id = proposal();
        write(post(PATH + "/" + id + "/accept-counter"), "clerk", version(id))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_period_status"));
    }

    @Test
    void caretakerCannotAnswerAgainAfterACounterProposal() throws Exception {
        long id = proposal();
        respond(id, "counter", "caretaker", counter(day(20), day(22), "Urlaub", periodVersion(id))).andExpect(status().isOk());
        respond(id, "agree", "caretaker", version(id))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_period_status"));
        respond(id, "counter", "caretaker", counter(day(25), day(27), "Doch anders", periodVersion(id)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_period_status"));
    }

    @Test
    void agreedPeriodCannotBeCounteredOrAcceptedAgain() throws Exception {
        long id = proposal();
        respond(id, "agree", "caretaker", version(id)).andExpect(status().isOk());
        respond(id, "counter", "caretaker", counter(day(20), day(22), "Urlaub", periodVersion(id)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_period_status"));
        write(post(PATH + "/" + id + "/accept-counter"), "clerk", version(id))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_period_status"));
    }

    @ParameterizedTest
    @CsvSource({"0,2", "-1,2", "-5,-3"})
    void caretakerCannotAgreeOnceThePeriodHasStarted(int start, int end) throws Exception {
        long id = startedProposal(start, end);
        respond(id, "agree", "caretaker", version(id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("start_not_in_future"));
        assertThat(periods.findById(id).orElseThrow().getStatus()).isEqualTo(InspectionPeriodEntity.Status.WAITING_FOR_CARETAKER);
    }

    @Test
    void caretakerCanStillCounterAPeriodThatHasStarted() throws Exception {
        long id = startedProposal(-5, -3);
        respond(id, "counter", "caretaker", counter(day(20), day(22), "Neuer Termin", periodVersion(id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COUNTER_PROPOSED"));
    }

    private long startedProposal(int start, int end) throws Exception {
        long id = proposal();
        var period = periods.findById(id).orElseThrow();
        period.setStartsOn(day(start));
        period.setEndsOn(day(end));
        periods.saveAndFlush(period);
        return id;
    }

    @Test
    void withdrawingRemovesTheProposalAndFreesTheFlats() throws Exception {
        long id = proposal();
        withdraw(id, "clerk").andExpect(status().isNoContent());

        assertThat(periods.existsById(id)).isFalse();
        mvc.perform(get(PATH + "/options").session(login("clerk")))
            .andExpect(jsonPath("$.residences[1].flatsWithOpenInspection").isEmpty());
        mvc.perform(get(PATH + "/assigned").session(login("caretaker")))
            .andExpect(jsonPath("$.content[*].id", not(hasItem((int) id))));
        propose(sonne(), day(10), day(12), userId("caretaker"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.flatCount").value(2));
    }

    @Test
    void startedInspectionsBlockWithdrawing() throws Exception {
        long id = proposal();
        var inspection = entities.createQuery("select i from InspectionEntity i where i.period.id = :id", InspectionEntity.class)
            .setParameter("id", id).setMaxResults(1).getSingleResult();
        inspection.setStatus(InspectionEntity.Status.DRAFT);
        entities.flush();

        withdraw(id, "clerk")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_started"));
        assertThat(periods.existsById(id)).isTrue();
    }

    @Test
    void announcedPeriodCannotBeWithdrawn() throws Exception {
        long id = proposal();
        var period = periods.findById(id).orElseThrow();
        period.setAnnouncedAt(Instant.now());
        periods.saveAndFlush(period);
        withdraw(id, "clerk")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_announced"));
    }

    @Test
    void withdrawingIsProtected() throws Exception {
        long id = proposal();
        withdraw(id, "caretaker").andExpect(status().isForbidden());
        write(post(PATH + "/" + id + "/withdraw"), "clerk", "{\"version\":99}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("period_changed"));

        var petershausen = residences.findFirstByName("Petershausen").orElseThrow();
        var foreign = periods.saveAndFlush(new InspectionPeriodEntity(petershausen, day(10), day(12),
            users.findByUserKey("clerk2").orElseThrow()));
        withdraw(foreign.getId(), "clerk").andExpect(status().isNotFound());
    }

    private ResultActions withdraw(long id, String key) throws Exception {
        return write(post(PATH + "/" + id + "/withdraw"), key, version(id));
    }

    private static String updateBody(LocalDate startsOn, LocalDate endsOn, String windowStart, String windowEnd,
            Long caretakerId, long version) {
        return "{\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"windowStart\":\"%s\",\"windowEnd\":\"%s\",\"caretakerId\":%d,\"version\":%d}"
            .formatted(startsOn, endsOn, windowStart, windowEnd, caretakerId, version);
    }

    private long proposal() throws Exception {
        return id(propose(sonne(), day(10), day(12), userId("caretaker")).andExpect(status().isCreated()));
    }

    private ResultActions propose(Long residenceId, LocalDate startsOn, LocalDate endsOn, Long caretakerId) throws Exception {
        return write(post(PATH), "clerk", body(residenceId, startsOn, endsOn, "09:00", "16:00", caretakerId));
    }

    private ResultActions update(long id, LocalDate startsOn, LocalDate endsOn) throws Exception {
        var content = "{\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"windowStart\":\"09:00\",\"windowEnd\":\"16:00\",\"caretakerId\":%d,\"version\":%d}"
            .formatted(startsOn, endsOn, userId("caretaker"), periodVersion(id));
        return write(put(PATH + "/" + id), "clerk", content);
    }

    private ResultActions respond(long id, String action, String key, String content) throws Exception {
        return write(post(PATH + "/" + id + "/" + action), key, content);
    }

    private ResultActions write(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String key, String content) throws Exception {
        return mvc.perform(request.session(login(key)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private static String body(Long residenceId, LocalDate startsOn, LocalDate endsOn, String windowStart,
            String windowEnd, Long caretakerId) {
        return "{\"residenceId\":%d,\"startsOn\":%s,\"endsOn\":%s,\"windowStart\":%s,\"windowEnd\":%s,\"caretakerId\":%s}"
            .formatted(residenceId, quoted(startsOn), quoted(endsOn), quoted(windowStart), quoted(windowEnd), caretakerId);
    }

    private static String counter(LocalDate startsOn, LocalDate endsOn, String reason, long version) {
        return "{\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"reason\":\"%s\",\"version\":%d}"
            .formatted(startsOn, endsOn, reason, version);
    }

    private static String counterWithWindow(LocalDate startsOn, LocalDate endsOn, String windowStart, String windowEnd,
            long version) {
        return "{\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"windowStart\":%s,\"windowEnd\":%s,\"reason\":\"Urlaub\",\"version\":%d}"
            .formatted(startsOn, endsOn, windowStart, windowEnd, version);
    }

    private static String quoted(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String version(long id) {
        return "{\"version\":" + periodVersion(id) + "}";
    }

    private long periodVersion(long id) {
        return periods.findById(id).orElseThrow().getVersion();
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static LocalDate day(int offset) {
        return LocalDate.now(ZoneId.of("Europe/Berlin")).plusDays(offset);
    }

    private Long sonne() {
        return residences.findFirstByName("Wohnanlage Sonne").orElseThrow().getId();
    }

    private Long userId(String key) {
        return users.findByUserKey(key).orElseThrow().getId();
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
