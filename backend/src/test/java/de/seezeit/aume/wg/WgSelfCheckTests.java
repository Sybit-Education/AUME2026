package de.seezeit.aume.wg;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.inspection.InspectionEntity;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.MembershipEntity;
import de.seezeit.aume.residence.MembershipRepository;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.standard.ChecklistService;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.user.RoleRepository;
import de.seezeit.aume.user.StudentEntity;
import de.seezeit.aume.user.UserEntity;
import de.seezeit.aume.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** US-84: Selbstcheck der eigenen WG mit der Checkliste. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-wg-self-check-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class WgSelfCheckTests {
    private static final String PATH = "/api/wg/self-check";

    @Autowired private MockMvc mvc;
    @Autowired private MembershipRepository memberships;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private UserRepository users;
    @Autowired private AppUserService appUsers;
    @Autowired private RoleRepository roles;
    @Autowired private ChecklistService checklists;
    @Autowired private EntityManager entities;

    private FlatEntity flat;
    private List<Long> criteria;
    private MockHttpSession me;
    private MockHttpSession flatmate;

    @BeforeEach
    void setUp() throws Exception {
        var today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        flat = flat("S-01");
        // The dev seed already puts the student into S-01; start without memberships.
        memberships.deleteAllInBatch();
        memberships.save(new MembershipEntity(flat, users.findByUserKey("student").orElseThrow(), today.minusDays(7)));
        var bea = student("flatmate", "Bea");
        memberships.save(new MembershipEntity(flat, bea, today.minusDays(7)));
        criteria = checklists.currentPublished().map(checklists::criteria).orElseThrow().stream()
            .map(CriterionEntity::getId).toList();
        me = login("student");
        flatmate = session(bea);
    }

    @Test
    void membersTickAndUntickCriteriaAndTheWholeFlatSeesIt() throws Exception {
        view(me).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.checkedCriterionIds.length()").value(0));

        check(me, criteria.get(1)).andExpect(status().isNoContent());
        check(me, criteria.get(0)).andExpect(status().isNoContent());
        check(flatmate, criteria.get(0)).andExpect(status().isNoContent());

        view(flatmate).andExpect(jsonPath("$.checkedCriterionIds.length()").value(2))
            .andExpect(jsonPath("$.checkedCriterionIds[0]").value(criteria.get(0).intValue()))
            .andExpect(jsonPath("$.checkedCriterionIds[1]").value(criteria.get(1).intValue()));

        mvc.perform(delete(PATH + "/" + criteria.get(1)).session(flatmate).with(csrf()))
            .andExpect(status().isNoContent());
        view(me).andExpect(jsonPath("$.checkedCriterionIds.length()").value(1))
            .andExpect(jsonPath("$.checkedCriterionIds[0]").value(criteria.get(0).intValue()));
    }

    @Test
    void resetClearsAllChecksOfTheOwnFlatOnly() throws Exception {
        var other = flat("S-02");
        var cem = student("outsider", "Cem");
        memberships.save(new MembershipEntity(other, cem, LocalDate.now().minusDays(7)));
        var outsider = session(cem);
        check(outsider, criteria.get(0)).andExpect(status().isNoContent());
        check(me, criteria.get(0)).andExpect(status().isNoContent());
        check(me, criteria.get(1)).andExpect(status().isNoContent());

        mvc.perform(delete(PATH).session(flatmate).with(csrf())).andExpect(status().isNoContent());

        view(me).andExpect(jsonPath("$.checkedCriterionIds.length()").value(0));
        view(outsider).andExpect(jsonPath("$.checkedCriterionIds.length()").value(1));
    }

    @Test
    void aPublishedInspectionStartsTheSelfCheckAgain() throws Exception {
        check(me, criteria.get(0)).andExpect(status().isNoContent());
        check(me, criteria.get(1)).andExpect(status().isNoContent());

        var inspection = new InspectionEntity(flat, null);
        inspection.setStatus(InspectionEntity.Status.PUBLISHED);
        inspection.setOverallResult(InspectionEntity.Result.PASSED);
        inspection.setPublishedAt(Instant.now().plus(1, ChronoUnit.SECONDS));
        entities.persist(inspection);

        view(me).andExpect(jsonPath("$.checkedCriterionIds.length()").value(0));
        // Ticked before the result, so it counts as a new tick afterwards.
        inspection.setPublishedAt(Instant.now().minus(1, ChronoUnit.SECONDS));
        entities.flush();
        entities.createQuery("update WgSelfCheckEntity c set c.checkedAt = :old")
            .setParameter("old", Instant.now().minus(1, ChronoUnit.HOURS)).executeUpdate();
        entities.clear();
        check(me, criteria.get(0)).andExpect(status().isNoContent());
        view(me).andExpect(jsonPath("$.checkedCriterionIds.length()").value(1))
            .andExpect(jsonPath("$.checkedCriterionIds[0]").value(criteria.get(0).intValue()));
    }

    @Test
    void rejectsUnknownCriteriaAndPeopleWithoutAFlatOrStudentRights() throws Exception {
        check(me, Long.MAX_VALUE).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(WgSelfCheckService.INVALID_CRITERION));

        view(session(student("loner", "Dana"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_member"));

        for (var key : new String[]{"clerk", "caretaker"}) {
            view(login(key)).andExpect(status().isForbidden());
        }
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    private ResultActions view(MockHttpSession session) throws Exception {
        return mvc.perform(get(PATH).session(session));
    }

    private ResultActions check(MockHttpSession session, Long criterionId) throws Exception {
        return mvc.perform(put(PATH + "/" + criterionId).session(session).with(csrf()));
    }

    private FlatEntity flat(String label) {
        var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        return flats.findByResidenceIdAndLabel(residence.getId(), label)
            .orElseGet(() -> flats.save(new FlatEntity(residence, label, 3)));
    }

    private UserEntity student(String key, String name) {
        var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        return users.save(new StudentEntity(key, name, Set.of(roles.findByName("STUDENT").orElseThrow()), residence));
    }

    private MockHttpSession session(UserEntity user) {
        // The dev login only supports the fixed demo accounts.
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            user.getUserKey(), null, appUsers.authorities(user)));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
