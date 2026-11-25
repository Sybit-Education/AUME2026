package de.seezeit.aume.residence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-membership-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MembershipTests {
    @Autowired private MockMvc mvc;
    @Autowired private InvitationRepository invitations;
    @Autowired private MembershipRepository memberships;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;

    @BeforeEach
    void startWithoutMemberships() {
        // The dev seed already puts the student into S-01.
        memberships.deleteAllInBatch();
    }

    @Test
    void studentJoinsWithCodeAndSeesOnlyTheirOwnFlat() throws Exception {
        var flatmate = student("flatmate", "Mitbewohner");
        memberships.save(new MembershipEntity(flat("S-01"), flatmate, LocalDate.now().minusDays(3)));
        memberships.save(new MembershipEntity(flat("S-02"), student("other", "Fremde WG"), LocalDate.now().minusDays(3)));
        invite("JOIN-1", "S-01");
        var session = login("student");

        mvc.perform(get("/api/memberships/me").session(session)).andExpect(status().isNoContent());
        join(session, " join-1 ").andExpect(status().isCreated())
            .andExpect(jsonPath("$.flat").value("S-01"))
            .andExpect(jsonPath("$.residence").value("Wohnanlage Sonne"));
        mvc.perform(get("/api/memberships/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0]").value("Mitbewohner"))
            .andExpect(jsonPath("$.members[1]").value("Studierende"));

        var used = invitations.findByTokenHash(MembershipService.hash("JOIN-1")).orElseThrow();
        assertThat(used.getUsedBy().getUserKey()).isEqualTo("student");
        assertThat(used.getUsedAt()).isNotNull();
    }

    @Test
    void anonymousStaffAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/memberships/me")).andExpect(status().isUnauthorized());
        for (var key : new String[]{"clerk", "caretaker"}) {
            mvc.perform(get("/api/memberships/me").session(login(key))).andExpect(status().isForbidden());
        }
        invite("JOIN-2", "S-01");
        mvc.perform(post("/api/memberships").session(login("student")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"JOIN-2\"}")).andExpect(status().isForbidden());
        assertThat(memberships.count()).isZero();
    }

    @Test
    void unusableCodesAllFailTheSameWay() throws Exception {
        var clerk = users.findByUserKey("clerk").orElseThrow();
        invitations.save(new InvitationEntity(flat("S-01"), MembershipService.hash("EXPIRED"),
            Instant.now().minusSeconds(1), clerk));
        var revoked = invite("REVOKED", "S-01");
        revoked.setRevokedAt(Instant.now());
        var used = invite("USED", "S-01");
        used.setUsedBy(clerk);
        used.setUsedAt(Instant.now());
        var foreign = residences.findFirstByName("Egg").orElseThrow();
        invitations.save(new InvitationEntity(flats.findByResidenceIdAndLabel(foreign.getId(), "E-12").orElseThrow(),
            MembershipService.hash("FOREIGN"), Instant.now().plus(Duration.ofDays(1)), clerk));

        var session = login("student");
        for (var code : new String[]{"UNKNOWN", "EXPIRED", "REVOKED", "USED", "FOREIGN", "   ", "X".repeat(65)}) {
            join(session, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_code"));
        }
        assertThat(memberships.count()).isZero();
    }

    @Test
    void studentCannotJoinTwice() throws Exception {
        invite("FIRST", "S-01");
        var second = invite("SECOND", "S-02");
        var session = login("student");
        join(session, "FIRST").andExpect(status().isCreated());
        join(session, "SECOND").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("already_member"));
        assertThat(second.getUsedAt()).isNull();
    }

    @Test
    void fullFlatRejectsJoinAndKeepsTheCode() throws Exception {
        var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        var full = flats.save(new FlatEntity(residence, "FULL", 1));
        memberships.save(new MembershipEntity(full, student("resident", "Bewohnt"), LocalDate.now().minusDays(1)));
        var invitation = invite("FULL-1", "FULL");
        join(login("student"), "FULL-1").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("flat_full"));
        assertThat(invitation.getUsedAt()).isNull();
    }

    @Test
    void leavingEndsAccessImmediatelyAndNeedsANewCodeToReturn() throws Exception {
        invite("BACK-1", "S-01");
        invite("BACK-2", "S-01");
        var session = login("student");
        join(session, "BACK-1").andExpect(status().isCreated());

        mvc.perform(post("/api/memberships/me/leave").session(session).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/memberships/me").session(session)).andExpect(status().isNoContent());
        mvc.perform(post("/api/memberships/me/leave").session(session).with(csrf()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_member"));

        join(session, "BACK-1").andExpect(status().isBadRequest());
        join(session, "BACK-2").andExpect(status().isCreated());
    }

    private ResultActions join(MockHttpSession session, String code) throws Exception {
        return mvc.perform(post("/api/memberships").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private InvitationEntity invite(String code, String label) {
        var clerk = users.findByUserKey("clerk").orElseThrow();
        return invitations.save(new InvitationEntity(flat(label), MembershipService.hash(code),
            Instant.now().plus(Duration.ofDays(1)), clerk));
    }

    private FlatEntity flat(String label) {
        var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        return flats.findByResidenceIdAndLabel(residence.getId(), label).orElseThrow();
    }

    private UserEntity student(String key, String name) {
        var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        return users.save(new StudentEntity(key, name, Set.of(roles.findByName("STUDENT").orElseThrow()), residence));
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
