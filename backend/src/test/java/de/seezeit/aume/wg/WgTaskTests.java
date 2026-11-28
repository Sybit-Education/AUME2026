package de.seezeit.aume.wg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.MembershipRepository;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.RoleRepository;
import de.seezeit.aume.user.StudentEntity;
import de.seezeit.aume.user.UserEntity;
import de.seezeit.aume.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-wg-task-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class WgTaskTests {
    @Autowired private MockMvc mvc;
    @Autowired private WgTaskRepository tasks;
    @Autowired private WgTaskAssignmentRepository completions;
    @Autowired private MembershipRepository memberships;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;

    private UserEntity me;
    private UserEntity flatmate;
    private FlatEntity flat;
    private LocalDate week;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        week = LocalDate.now(ZoneId.of("Europe/Berlin")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        flat = flat("S-01");
        me = users.findByUserKey("student").orElseThrow();
        // The dev seed already puts the student into S-01; start without memberships.
        memberships.deleteAllInBatch();
        flatmate = student("flatmate", "Bea");
        memberships.save(new MembershipEntity(flat, me, week.minusWeeks(1)));
        memberships.save(new MembershipEntity(flat, flatmate, week.minusWeeks(1)));
        session = login("student");
    }

    @Test
    void memberCreatesEditsAndDeletesTasksForTheirFlat() throws Exception {
        save(post("/api/wg/tasks"), "  Küche reinigen ", flatmate.getId(), "WEEKLY").andExpect(status().isCreated());
        var task = tasks.findActive(flat.getId()).getFirst();

        plan().andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.tasks[0].title").value("Küche reinigen"))
            .andExpect(jsonPath("$.tasks[0].assigneeName").value("Bea"))
            .andExpect(jsonPath("$.tasks[0].mine").value(false))
            .andExpect(jsonPath("$.tasks[0].checkable").value(false));

        save(put("/api/wg/tasks/" + task.getId()), "Bad putzen", me.getId(), "BIWEEKLY")
            .andExpect(status().isNoContent());
        plan().andExpect(jsonPath("$.tasks[0].title").value("Bad putzen"))
            .andExpect(jsonPath("$.tasks[0].rhythm").value("BIWEEKLY"))
            .andExpect(jsonPath("$.tasks[0].mine").value(true));

        mvc.perform(delete("/api/wg/tasks/" + task.getId()).session(session).with(csrf()))
            .andExpect(status().isNoContent());
        plan().andExpect(jsonPath("$.tasks.length()").value(0)).andExpect(jsonPath("$.allTitles.length()").value(0));
    }

    @Test
    void rhythmDecidesInWhichWeeksATaskIsDue() throws Exception {
        tasks.save(new WgTaskEntity(flat, "Alle zwei Wochen", 0, me, WgTaskRhythm.BIWEEKLY, week));
        tasks.save(new WgTaskEntity(flat, "Monatlich", 1, me, WgTaskRhythm.MONTHLY, week));

        plan(week).andExpect(jsonPath("$.tasks.length()").value(2));
        plan(week.plusWeeks(1)).andExpect(jsonPath("$.tasks.length()").value(0))
            .andExpect(jsonPath("$.allTitles.length()").value(2));
        plan(week.plusWeeks(2)).andExpect(jsonPath("$.tasks.length()").value(1));
        plan(week.plusWeeks(4)).andExpect(jsonPath("$.tasks.length()").value(2));
        plan(week.minusWeeks(2)).andExpect(jsonPath("$.tasks.length()").value(0));
    }

    @Test
    void assigneeTicksAndUndoesAndTheFlatSeesWhoDidIt() throws Exception {
        var task = tasks.save(new WgTaskEntity(flat, "Müll", 0, me, WgTaskRhythm.WEEKLY, week));
        var completion = "/api/wg/tasks/" + task.getId() + "/completion";

        mvc.perform(put(completion).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put(completion).session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(completions.count()).isEqualTo(1);
        plan().andExpect(jsonPath("$.tasks[0].done").value(true))
            .andExpect(jsonPath("$.tasks[0].doneBy").value("Studierende"))
            .andExpect(jsonPath("$.tasks[0].checkable").value(true));

        mvc.perform(delete(completion).session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(completions.count()).isZero();
        plan().andExpect(jsonPath("$.tasks[0].done").value(false));
    }

    @Test
    void onlyTheAssigneeCanTickAndOnlyWhenDue() throws Exception {
        var foreign = tasks.save(new WgTaskEntity(flat, "Flur", 0, flatmate, WgTaskRhythm.WEEKLY, week));
        var later = tasks.save(new WgTaskEntity(flat, "Später", 1, me, WgTaskRhythm.WEEKLY, week.plusWeeks(1)));

        mvc.perform(put("/api/wg/tasks/" + foreign.getId() + "/completion").session(session).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_assignee"));
        mvc.perform(put("/api/wg/tasks/" + later.getId() + "/completion").session(session).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_due"));

        var done = new WgTaskAssignmentEntity(foreign, flatmate, week);
        done.setCompletedAt(java.time.Instant.now());
        completions.save(done);
        mvc.perform(delete("/api/wg/tasks/" + foreign.getId() + "/completion").session(session).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_assignee"));
        assertThat(completions.count()).isEqualTo(1);
    }

    @Test
    void foreignFlatsStaffAndAnonymousUsersHaveNoAccess() throws Exception {
        var outsider = student("outsider", "Fremd");
        var otherFlat = flat("S-02");
        memberships.save(new MembershipEntity(otherFlat, outsider, week));
        var foreign = tasks.save(new WgTaskEntity(otherFlat, "Geheim", 0, outsider, WgTaskRhythm.WEEKLY, week));

        plan().andExpect(jsonPath("$.tasks.length()").value(0)).andExpect(jsonPath("$.allTitles.length()").value(0));
        save(put("/api/wg/tasks/" + foreign.getId()), "Übernommen", me.getId(), "WEEKLY").andExpect(status().isNotFound());
        mvc.perform(delete("/api/wg/tasks/" + foreign.getId()).session(session).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/wg/tasks/" + foreign.getId() + "/completion").session(session).with(csrf()))
            .andExpect(status().isNotFound());
        save(post("/api/wg/tasks"), "Fremde Person", outsider.getId(), "WEEKLY")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_assignee"));
        assertThat(foreign.getTitle()).isEqualTo("Geheim");
        assertThat(foreign.getArchivedAt()).isNull();

        mvc.perform(get("/api/wg/tasks")).andExpect(status().isUnauthorized());
        for (var key : new String[]{"clerk", "caretaker"}) {
            mvc.perform(get("/api/wg/tasks").session(login(key))).andExpect(status().isForbidden());
        }
    }

    @Test
    void leavingEndsAccessAndRemovesThePersonFromThePlan() throws Exception {
        var mine = tasks.save(new WgTaskEntity(flat, "Bad", 0, flatmate, WgTaskRhythm.WEEKLY, week));
        var flatmateMembership = memberships.findActive(flatmate.getId(), week.plusDays(0)).orElseThrow();
        flatmateMembership.setValidTo(LocalDate.now(ZoneId.of("Europe/Berlin")));
        plan().andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.tasks[0].assigneeId").doesNotExist())
            .andExpect(jsonPath("$.tasks[0].assigneeName").doesNotExist());
        assertThat(mine.getAssignee().getId()).isEqualTo(flatmate.getId());

        mvc.perform(post("/api/memberships/me/leave").session(session).with(csrf())).andExpect(status().isNoContent());
        plan().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_member"));
    }

    @Test
    void suggestionsComeFromTheChecklistAndDisappearOnceLinked() throws Exception {
        var body = plan().andExpect(jsonPath("$.suggestions.length()").value(10))
            .andExpect(jsonPath("$.suggestions[0].area").value("Küche"))
            .andExpect(jsonPath("$.suggestions[0].title").value("Arbeitsflächen"))
            .andReturn().getResponse().getContentAsString();
        long criterionId = ((Number) JsonPath.read(body, "$.suggestions[0].criterionId")).longValue();
        mvc.perform(get("/api/wg/tasks").session(session).header("Accept-Language", "en"))
            .andExpect(jsonPath("$.suggestions[0].area").value("Kitchen"))
            .andExpect(jsonPath("$.suggestions[0].title").value("Work surfaces"));

        mvc.perform(post("/api/wg/tasks").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Arbeitsflächen\",\"assigneeId\":" + me.getId()
                + ",\"rhythm\":\"WEEKLY\",\"criterionId\":" + criterionId + "}")).andExpect(status().isCreated());
        var task = tasks.findActive(flat.getId()).getFirst();
        assertThat(task.getCriterion().getId()).isEqualTo(criterionId);
        plan().andExpect(jsonPath("$.suggestions.length()").value(9));

        // Only deleting the task brings the suggestion back.
        save(put("/api/wg/tasks/" + task.getId()), "Eigener Titel", me.getId(), "WEEKLY")
            .andExpect(status().isNoContent());
        assertThat(task.getCriterion().getId()).isEqualTo(criterionId);
        mvc.perform(get("/api/wg/tasks").session(session).header("Accept-Language", "en"))
            .andExpect(jsonPath("$.suggestions.length()").value(9));
        mvc.perform(delete("/api/wg/tasks/" + task.getId()).session(session).with(csrf()))
            .andExpect(status().isNoContent());
        plan().andExpect(jsonPath("$.suggestions.length()").value(10));
    }

    @Test
    void unknownCriterionIsRejected() throws Exception {
        mvc.perform(post("/api/wg/tasks").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Bad\",\"assigneeId\":" + me.getId() + ",\"rhythm\":\"WEEKLY\",\"criterionId\":999999}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_criterion"));
        assertThat(tasks.count()).isZero();
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        save(post("/api/wg/tasks"), "   ", me.getId(), "WEEKLY")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_title"));
        save(post("/api/wg/tasks"), "X".repeat(121), me.getId(), "WEEKLY")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_title"));
        mvc.perform(post("/api/wg/tasks").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Bad\",\"assigneeId\":" + me.getId() + "}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_rhythm"));
        assertThat(tasks.count()).isZero();
    }

    private ResultActions plan() throws Exception {
        return mvc.perform(get("/api/wg/tasks").session(session));
    }

    private ResultActions plan(LocalDate requested) throws Exception {
        return mvc.perform(get("/api/wg/tasks").param("week", requested.toString()).session(session));
    }

    private ResultActions save(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String title, Long assigneeId, String rhythm) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"" + title + "\",\"assigneeId\":" + assigneeId + ",\"rhythm\":\"" + rhythm + "\"}"));
    }

    private FlatEntity flat(String label) {
        var residence = residences.findFirstByName("Sonnenbühl West I").orElseThrow();
        return flats.findByResidenceIdAndLabel(residence.getId(), label).orElseThrow();
    }

    private UserEntity student(String key, String name) {
        var residence = residences.findFirstByName("Sonnenbühl West I").orElseThrow();
        return users.save(new StudentEntity(key, name, Set.of(roles.findByName("STUDENT").orElseThrow()), residence));
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
