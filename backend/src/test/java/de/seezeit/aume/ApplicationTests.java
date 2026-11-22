package de.seezeit.aume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.user.*;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-dev-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
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
    @CsvSource({"student,STUDENT,GREETING_READ", "caretaker,CARETAKER,RESIDENCE_READ",
        "clerk,CLERK,RESIDENCE_ASSIGN_STAFF"})
    void mockUsersReceiveTheirOwnRolesAndPrivileges(String key, String role, String privilege) throws Exception {
        var session = login(key);
        mvc.perform(get("/api/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userKey").value(key))
            .andExpect(jsonPath("$.roles[0]").value(role))
            .andExpect(jsonPath("$.privileges", org.hamcrest.Matchers.hasItem(privilege)))
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
        mvc.perform(get("/api/greeting").session(session).header("X-Dev-User", "clerk"))
            .andExpect(status().isOk())
            .andExpect(content().string("Hallo Studierende, willkommen in Wohnanlage Sonne."));
        mvc.perform(get("/api/residences").session(session)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
        "en,en,'Hello Studierende, welcome to Wohnanlage Sonne.'",
        "en-US,en,'Hello Studierende, welcome to Wohnanlage Sonne.'",
        "de-CH,de,'Hallo Studierende, willkommen in Wohnanlage Sonne.'",
        "fr,de,'Hallo Studierende, willkommen in Wohnanlage Sonne.'"
    })
    void greetingUsesTheRequestedSupportedLanguage(String requested, String resolved, String greeting) throws Exception {
        mvc.perform(get("/api/greeting").session(login("student")).header("Accept-Language", requested))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Language", resolved))
            .andExpect(content().string(greeting));
    }

    @Test
    void caretakerOnlyReadsTheirOwnResidenceWithItsFlatsAndStaff() throws Exception {
        residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var session = login("caretaker");
        mvc.perform(get("/api/residences").session(session).param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.size").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Wohnanlage Sonne"))
            .andExpect(jsonPath("$.content[0].flats").value(org.hamcrest.Matchers.contains("S-01", "S-02")))
            .andExpect(jsonPath("$.content[0].caretaker.name").value("Objektbetreuung"))
            .andExpect(jsonPath("$.content[0].deputy.name").value("Objektbetreuung Petershausen"));
        mvc.perform(get("/api/greeting").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/residences").session(session).param("page", "1").param("size", "1"))
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void clerkReadsOnlyAssignedResidencesSortedByNameWithFlatsAndStaff() throws Exception {
        residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var session = login("clerk");
        mvc.perform(get("/api/residences").session(session))
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
        mvc.perform(get("/api/residences").session(session).param("page", "1").param("size", "1"))
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Wohnanlage Sonne"));
    }

    @Test
    void loginAndLogoutRequireCsrfAndInvalidateTheSession() throws Exception {
        mvc.perform(post("/api/auth/dev-login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userKey\":\"student\"}")).andExpect(status().isForbidden());
        var csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String previousId = session.getId();
        String header = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.headerName");
        String token = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/auth/dev-login").session(session).with(request -> {
                request.setRequestedSessionId(previousId);
                request.setRequestedSessionIdValid(true);
                return request;
            }).header(header, token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"student\"}"))
            .andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(previousId);
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).header(header, token)).andExpect(status().isForbidden());
        var freshCsrf = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        String freshToken = JsonPath.read(freshCsrf.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/auth/logout").session(session).header(header, freshToken))
            .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void clientsCannotChooseRolesOrLogInAsAnArbitraryDatabaseUser() throws Exception {
        var session = (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userKey\":\"student\",\"roles\":[\"CLERK\"],\"privileges\":[\"RESIDENCE_READ\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("STUDENT"))
            .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/residences").session(session)).andExpect(status().isForbidden());
        var student = users.findByUserKey("student").orElseThrow();
        users.save(new StudentEntity("another-user", "Other", student.getRoles(), student.getResidence()));
        mvc.perform(post("/api/auth/dev-login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userKey\":\"another-user\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/dev-login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void multipleRolesCombineAuthoritiesWithoutDuplicates() throws Exception {
        var student = users.findByUserKey("student").orElseThrow();
        student.getRoles().add(roles.findByName("CLERK").orElseThrow());
        student.getRoles().add(roles.findByName("CARETAKER").orElseThrow());
        var session = login("student");
        mvc.perform(get("/api/me").session(session))
            .andExpect(jsonPath("$.roles.length()").value(3))
            .andExpect(jsonPath("$.privileges.length()").value(5));
        mvc.perform(get("/api/greeting").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/residences").session(session)).andExpect(status().isOk());
    }

    @Test
    void repositoriesAndWritesAreNotExposed() throws Exception {
        var session = login("clerk");
        for (String path : new String[]{"/api/users", "/api/users/search/findByUserKey?userKey=student",
                "/api/users/1/roles", "/api/roles", "/api/privileges", "/api/residences/1"}) {
            mvc.perform(get(path).session(session)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/users").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/residences").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
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
        assertThat(privileges.count()).isEqualTo(5);
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
