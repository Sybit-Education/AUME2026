package de.seezeit.aume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.user.*;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-dev-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestJwtDecoder.class)
@Transactional
class ApplicationTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private StaffAssignmentRepository staffAssignments;
    @Autowired private RoleRepository roles;
    @Autowired private PrivilegeRepository privileges;
    @Autowired private SecurityData securityData;
    @Autowired private SampleData sampleData;

    @ParameterizedTest
    @CsvSource({"student,STUDENT,GREETING_READ", "student,STUDENT,WG_MEMBERSHIP", "caretaker,CARETAKER,INSPECTION_PERFORM",
        "caretaker,CARETAKER,INSPECTION_OVERVIEW_READ",
        "clerk,CLERK,RESIDENCE_ASSIGN_STAFF", "clerk,CLERK,CLERK_INSPECTION_OVERVIEW_READ"})
    void mockUsersReceiveTheirOwnRolesAndPrivileges(String key, String role, String privilege) throws Exception {
        var session = login(key);
        mvc.perform(get("/api/me").with(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userKey").value(key))
            .andExpect(jsonPath("$.roles[0]").value(role))
            .andExpect(jsonPath("$.privileges", hasItem(privilege)))
            .andExpect(jsonPath("$.identitySubject").doesNotExist());
    }

    @Test
    void anonymousRequestsAndSpoofedHeadersDoNotAuthenticate() throws Exception {
        for (String path : new String[]{"/api/me", "/api/greeting", "/api/residences", "/api/users"}) {
            mvc.perform(get(path).header("X-Dev-User", "student")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void studentCanReadGreetingButCannotReadResidences() throws Exception {
        var session = login("student");
        mvc.perform(get("/api/greeting").with(session).header("X-Dev-User", "clerk"))
            .andExpect(status().isOk())
            .andExpect(content().string("Hallo Studierende, willkommen in Wohnanlage Sonne."));
        mvc.perform(get("/api/residences").with(session)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
        "en,en,'Hello Studierende, welcome to Wohnanlage Sonne.'",
        "en-US,en,'Hello Studierende, welcome to Wohnanlage Sonne.'",
        "de-CH,de,'Hallo Studierende, willkommen in Wohnanlage Sonne.'",
        "fr,de,'Hallo Studierende, willkommen in Wohnanlage Sonne.'"
    })
    void greetingUsesTheRequestedSupportedLanguage(String requested, String resolved, String greeting) throws Exception {
        mvc.perform(get("/api/greeting").with(login("student")).header("Accept-Language", requested))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Language", resolved))
            .andExpect(content().string(greeting));
    }

    @Test
    void caretakerCanOnlyReadTheirAssignedResidence() throws Exception {
        residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var session = login("caretaker");
        mvc.perform(get("/api/residences").with(session).param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.size").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Wohnanlage Sonne"));
        mvc.perform(get("/api/greeting").with(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/residences").with(session).param("page", "1").param("size", "1"))
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void clerkReadsOnlyAssignedResidencesSortedByNameWithFlatsAndStaff() throws Exception {
        residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var session = login("clerk");
        mvc.perform(get("/api/residences").with(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Egg"))
            .andExpect(jsonPath("$.content[0].flats").value(org.hamcrest.Matchers.contains("E-12", "E-14")))
            .andExpect(jsonPath("$.content[0].caretaker.name").value("Objektbetreuung Egg"))
            .andExpect(jsonPath("$.content[0].deputy").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.content[1].name").value("Wohnanlage Sonne"))
            .andExpect(jsonPath("$.content[1].flats").value(org.hamcrest.Matchers.contains("S-01", "S-02")))
            .andExpect(jsonPath("$.content[1].caretaker.name").value("Objektbetreuung"))
            .andExpect(jsonPath("$.content[1].deputy.name").value("Objektbetreuung Petershausen"))
            .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.hasItem("Petershausen"))));
        mvc.perform(get("/api/residences").with(session).param("page", "1").param("size", "1"))
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Wohnanlage Sonne"));
    }

    @Test
    void multipleRolesCombineAuthoritiesWithoutDuplicates() throws Exception {
        var student = users.findByUserKey("student").orElseThrow();
        student.getRoles().add(roles.findByName("CLERK").orElseThrow());
        student.getRoles().add(roles.findByName("CARETAKER").orElseThrow());
        var session = login("student");
        mvc.perform(get("/api/me").with(session))
            .andExpect(jsonPath("$.roles.length()").value(3))
            .andExpect(jsonPath("$.privileges.length()").value(13));
        mvc.perform(get("/api/greeting").with(session)).andExpect(status().isOk());
        mvc.perform(get("/api/residences").with(session)).andExpect(status().isOk());
    }

    @Test
    void repositoriesAndWritesAreNotExposed() throws Exception {
        var session = login("clerk");
        for (String path : new String[]{"/api/users", "/api/users/search/findByUserKey?userKey=student",
                "/api/users/1/roles", "/api/roles", "/api/privileges", "/api/residences/1"}) {
            mvc.perform(get(path).with(session)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/users").with(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/residences").with(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void seedersAreIdempotent() {
        securityData.run();
        sampleData.run();
        assertThat(users.count()).isEqualTo(6);
        assertThat(residences.count()).isEqualTo(3);
        assertThat(flats.count()).isEqualTo(9);
        assertThat(staffAssignments.count()).isEqualTo(8);
        assertThat(roles.count()).isEqualTo(3);
        assertThat(privileges.count()).isEqualTo(13);
    }

    private RequestPostProcessor login(String key) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + key);
            return request;
        };
    }
}
