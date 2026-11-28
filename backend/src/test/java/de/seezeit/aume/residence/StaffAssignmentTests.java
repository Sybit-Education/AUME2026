package de.seezeit.aume.residence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import de.seezeit.aume.auth.TestJwtDecoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-staff-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestJwtDecoder.class)
@Transactional
class StaffAssignmentTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ResidenceRepository residences;
    @Autowired private StaffAssignmentRepository staff;

    @Test
    void clerkReplacesCaretakerAndDeputy() throws Exception {
        var session = login("clerk");
        // Sonne: bisher caretaker + caretaker2
        assign(session, "Sonnenbühl West I", id("caretaker3"), id("caretaker2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Sonnenbühl West I"))
            .andExpect(jsonPath("$.flats").value(contains("S-01", "S-02")))
            .andExpect(jsonPath("$.caretaker.name").value("Objektbetreuung Egg"))
            .andExpect(jsonPath("$.deputy.name").value("Objektbetreuung Petershausen"));
        mvc.perform(get("/api/residences").with(session))
            .andExpect(jsonPath("$.content[1].name").value("Sonnenbühl West I"))
            .andExpect(jsonPath("$.content[1].caretaker.id").value(id("caretaker3")))
            .andExpect(jsonPath("$.content[1].deputy.id").value(id("caretaker2")));
    }

    @Test
    void caretakerCanSeeAssignedResidencesWithRoleAndFlats() throws Exception {
        var petershausen = residences.findFirstByName("Petershausen").orElseThrow();
        staff.delete(staff.findByResidenceIdAndKind(petershausen.getId(), StaffKind.DEPUTY).orElseThrow());
        staff.save(new StaffAssignmentEntity(users.findByUserKey("caretaker").orElseThrow(),
            petershausen, StaffKind.DEPUTY));

        mvc.perform(get("/api/residences/my-assignments").with(login("caretaker")).param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Petershausen"))
            .andExpect(jsonPath("$.content[0].kind").value("DEPUTY"))
            .andExpect(jsonPath("$.content[0].flats").value(contains("3.08", "3.09", "3.10", "4.12", "4.13")))
            .andExpect(jsonPath("$.content[1].name").value("Sonnenbühl West I"))
            .andExpect(jsonPath("$.content[1].kind").value("CARETAKER"))
            .andExpect(jsonPath("$.content[1].flats").value(contains("S-01", "S-02")));
    }

    @Test
    void clerkCannotReadCaretakerAssignments() throws Exception {
        mvc.perform(get("/api/residences/my-assignments").with(login("clerk")))
            .andExpect(status().isForbidden());
    }

    @Test
    void caretakerAndDeputyCanBeSwapped() throws Exception {
        var session = login("clerk");
        // Sonne: bisher caretaker + caretaker2, jetzt vertauscht
        assign(session, "Sonnenbühl West I", id("caretaker2"), id("caretaker"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.caretaker.id").value(id("caretaker2")))
            .andExpect(jsonPath("$.deputy.id").value(id("caretaker")));
    }

    @Test
    void deputyMayBeLeftEmptyAndRemovesAnExistingDeputy() throws Exception {
        var session = login("clerk");
        assign(session, "Sonnenbühl West I", id("caretaker"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.caretaker.id").value(id("caretaker")))
            .andExpect(jsonPath("$.deputy").value(nullValue()));
        assertThat(staff.findByResidenceIdAndKind(residenceId("Sonnenbühl West I"), StaffKind.DEPUTY)).isEmpty();
    }

    @Test
    void changingCaretakersKeepsTheClerkAssignment() throws Exception {
        var session = login("clerk");
        assign(session, "Sonnenbühl West I", id("caretaker2"), id("caretaker")).andExpect(status().isOk());
        assertThat(staff.existsByResidenceIdAndUserIdAndKind(residenceId("Sonnenbühl West I"), id("clerk"),
            StaffKind.CLERK)).isTrue();
        // Weiterhin sichtbar und änderbar
        mvc.perform(get("/api/residences").with(session))
            .andExpect(jsonPath("$.page.totalElements").value(2));
        assign(session, "Sonnenbühl West I", id("caretaker"), id("caretaker2")).andExpect(status().isOk());
    }

    @Test
    void clerkCannotChangeAResidenceThatIsNotAssignedToThem() throws Exception {
        var session = login("clerk");
        assign(session, "Petershausen", id("caretaker"), null).andExpect(status().isNotFound());
        var petershausen = residenceId("Petershausen");
        assertThat(staff.findByResidenceIdAndKind(petershausen, StaffKind.CARETAKER).orElseThrow().getUser().getId())
            .isEqualTo(id("caretaker2"));
        assertThat(staff.findByResidenceIdAndKind(petershausen, StaffKind.DEPUTY).orElseThrow().getUser().getId())
            .isEqualTo(id("caretaker3"));
    }

    @Test
    void deputyCanBeAddedWhereNoneExists() throws Exception {
        var session = login("clerk");
        assign(session, "Egg", id("caretaker3"), id("caretaker"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deputy.id").value(id("caretaker")));
    }

    @Test
    void caretakerAndDeputyMustBeDifferentPeopleAndNothingIsChanged() throws Exception {
        var session = login("clerk");
        assign(session, "Sonnenbühl West I", id("caretaker3"), id("caretaker3"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ResidenceService.DEPUTY_MUST_DIFFER));
        assertUnchangedSonne();
    }

    @Test
    void caretakerIsRequired() throws Exception {
        var session = login("clerk");
        assign(session, "Sonnenbühl West I", null, id("caretaker2"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ResidenceService.CARETAKER_REQUIRED));
        assertUnchangedSonne();
    }

    @ParameterizedTest
    @CsvSource({"clerk", "student", "99999"})
    void onlyCaretakersCanBeAssignedAndNothingIsChanged(String who) throws Exception {
        var session = login("clerk");
        Long other = who.matches("\\d+") ? Long.valueOf(who) : id(who);
        assign(session, "Sonnenbühl West I", other, id("caretaker2"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ResidenceService.UNKNOWN_CARETAKER));
        assign(session, "Sonnenbühl West I", id("caretaker3"), other)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ResidenceService.UNKNOWN_CARETAKER));
        assertUnchangedSonne();
    }

    @Test
    void unknownResidenceIsNotFound() throws Exception {
        var session = login("clerk");
        mvc.perform(put("/api/residences/99999/staff").with(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(id("caretaker"), null)))
            .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"caretaker", "student"})
    void usersWithoutThePrivilegeCannotChangeOrListCaretakers(String key) throws Exception {
        var session = login(key);
        assign(session, "Sonnenbühl West I", id("caretaker3"), null).andExpect(status().isForbidden());
        mvc.perform(get("/api/caretakers").with(session)).andExpect(status().isForbidden());
        assertUnchangedSonne();
    }

    @Test
    void anonymousAccessIsRejectedAndChangesRequireCsrf() throws Exception {
        mvc.perform(put("/api/residences/1/staff").contentType(MediaType.APPLICATION_JSON)
            .content(body(id("caretaker"), null))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/caretakers")).andExpect(status().isUnauthorized());
    }

    @Test
    void clerkListsOnlyCaretakersSortedAndPaged() throws Exception {
        var session = login("clerk");
        mvc.perform(get("/api/caretakers").with(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.content[0].name").value("Objektbetreuung"))
            .andExpect(jsonPath("$.content[1].name").value("Objektbetreuung Egg"))
            .andExpect(jsonPath("$.content[2].name").value("Objektbetreuung Petershausen"))
            .andExpect(jsonPath("$.content[0].userKey").doesNotExist());
        mvc.perform(get("/api/caretakers").with(session).param("page", "1").param("size", "2"))
            .andExpect(jsonPath("$.content.length()").value(1));
    }

    private void assertUnchangedSonne() {
        var sonne = residenceId("Sonnenbühl West I");
        assertThat(staff.findByResidenceIdAndKind(sonne, StaffKind.CARETAKER).orElseThrow().getUser().getId())
            .isEqualTo(id("caretaker"));
        assertThat(staff.findByResidenceIdAndKind(sonne, StaffKind.DEPUTY).orElseThrow().getUser().getId())
            .isEqualTo(id("caretaker2"));
    }

    private ResultActions assign(RequestPostProcessor session, String residence, Long caretaker, Long deputy)
            throws Exception {
        return mvc.perform(put("/api/residences/" + residenceId(residence) + "/staff").with(session)
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(caretaker, deputy)));
    }

    private static String body(Long caretaker, Long deputy) {
        return "{\"caretakerId\":" + caretaker + ",\"deputyId\":" + deputy + "}";
    }

    private Long id(String userKey) {
        return users.findByUserKey(userKey).orElseThrow().getId();
    }

    private Long residenceId(String name) {
        return residences.findFirstByName(name).orElseThrow().getId();
    }

    private RequestPostProcessor login(String key) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + key);
            return request;
        };
    }
}
