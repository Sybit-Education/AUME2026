package de.seezeit.aume.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.UserRepository;
import jakarta.persistence.EntityManager;
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

/** Individuelle Kontrolle für einzelne WGs planen, abstimmen und ankündigen (US-18b). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-individual-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class IndividualInspectionTests {
    private static final String PATH = "/api/inspection-periods";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private InspectionPeriodRepository periods;
    @Autowired private InspectionNotificationDeliveryService delivery;
    @Autowired private EntityManager entities;

    /** Wie in InspectionPlanningTests: Sonne von den Beispielkontrollen befreien. */
    @BeforeEach
    void freeSonne() {
        entities.createQuery("""
                update InspectionEntity i set i.status = :published
                where i.flat.id in (select f.id from FlatEntity f where f.residence.name = 'Sonnenbühl West I')
                """)
            .setParameter("published", InspectionEntity.Status.PUBLISHED)
            .executeUpdate();
    }

    @Test
    void optionsListTheFlatsOfEachResidence() throws Exception {
        mvc.perform(get(PATH + "/options").session(login("clerk")))
            .andExpect(jsonPath("$.residences[1].flats[*].label").value(contains("S-01", "S-02")))
            .andExpect(jsonPath("$.residences[1].flats[0].id").value(flat("S-01").intValue()));
    }

    @Test
    void individualInspectionCoversOnlyTheSelectedFlats() throws Exception {
        long id = id(individual("[" + flat("S-02") + "]", "\"COMPLAINT\"")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.occasion").value("COMPLAINT"))
            .andExpect(jsonPath("$.status").value("WAITING_FOR_CARETAKER"))
            .andExpect(jsonPath("$.flatCount").value(1))
            .andExpect(jsonPath("$.flatLabels").value(contains("S-02"))));

        assertThat(entities.createQuery("select i.flat.label from InspectionEntity i where i.period.id = :id", String.class)
            .setParameter("id", id).getResultList()).containsExactly("S-02");
        // S-01 bleibt frei für eine weitere Kontrolle.
        mvc.perform(get(PATH + "/options").session(login("clerk")))
            .andExpect(jsonPath("$.residences[1].flatsWithOpenInspection").value(contains("S-02")));
    }

    @Test
    void regularInspectionHasNoFlatLabels() throws Exception {
        write(post(PATH), "clerk", body("null", "null", "null"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("REGULAR"))
            .andExpect(jsonPath("$.occasion").isEmpty())
            .andExpect(jsonPath("$.flatLabels").isEmpty());
    }

    @Test
    void occasionAndAtLeastOneFlatOfTheResidenceAreRequired() throws Exception {
        var before = periods.count();
        individual("[" + flat("S-01") + "]", "null")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("occasion_required"));
        individual("[]", "\"SAMPLE\"")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("flats_required"));
        individual("null", "\"SAMPLE\"")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("flats_required"));
        var egg = residences.findFirstByName("Egg").orElseThrow().getId();
        var foreign = flats.findByResidenceIdAndLabel(egg, "E-12").orElseThrow().getId();
        individual("[" + flat("S-01") + "," + foreign + "]", "\"SAMPLE\"")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unknown_flat"));
        assertThat(periods.count()).isEqualTo(before);
    }

    @Test
    void flatWithOpenInspectionCannotBeSelected() throws Exception {
        individual("[" + flat("S-01") + "]", "\"SAMPLE\"").andExpect(status().isCreated());
        individual("[" + flat("S-01") + "," + flat("S-02") + "]", "\"OTHER\"")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("flat_has_open_inspection"));
    }

    @Test
    void caretakerAgreesLikeForARegularInspectionAndSeesTheFlats() throws Exception {
        long id = id(individual("[" + flat("S-01") + "]", "\"SAMPLE\""));
        mvc.perform(get(PATH + "/assigned").session(login("caretaker")))
            .andExpect(jsonPath("$.content[?(@.id == %d)].kind".formatted(id)).value(contains("INDIVIDUAL")))
            .andExpect(jsonPath("$.content[?(@.id == %d)].flatLabels[0]".formatted(id)).value(contains("S-01")));
        write(post(PATH + "/" + id + "/agree"), "caretaker", "{\"version\":" + version(id) + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AGREED"));
    }

    @Test
    void announcementUsesTheIndividualTemplateAndReachesOnlyTheSelectedFlats() throws Exception {
        long id = id(individual("[" + flat("S-02") + "]", "\"COMPLAINT\""));
        write(post(PATH + "/" + id + "/agree"), "caretaker", "{\"version\":" + version(id) + "}");

        mvc.perform(get(PATH + "/" + id + "/announcement-draft").session(login("clerk")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.flatCount").value(1))
            .andExpect(jsonPath("$.flatLabels").value(contains("S-02")))
            .andExpect(jsonPath("$.message", containsString("für eure WG in der Wohnanlage Sonnenbühl West I")))
            .andExpect(jsonPath("$.message", not(containsString("reguläre"))))
            .andExpect(jsonPath("$.message", not(containsString("Beschwerde"))));

        write(post(PATH + "/" + id + "/announce"), "clerk", "{\"message\":\"Text\",\"version\":%d}".formatted(version(id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flatCount").value(1));
        assertThat(periods.findById(id).orElseThrow().getAnnouncedAt()).isNotNull();

        deliverAll(id);
        assertThat(entities.createQuery("""
                select m.inspection.flat.label from InspectionNotificationEntity m
                where m.inspection.period.id = :id
                """, String.class).setParameter("id", id).getResultList()).containsExactly("S-02");

        // Objektbetreuung: In den anstehenden Kontrollen ist sie als individuelle Kontrolle erkennbar.
        mvc.perform(get("/api/inspections?size=100").session(login("caretaker")))
            .andExpect(jsonPath("$.content[?(@.flatLabel == 'S-02' && @.round == 0)].periodKind").value(contains("INDIVIDUAL")));
    }

    @Test
    void overviewListsTheIndividualInspectionAsOpenSingleNotAsRound() throws Exception {
        long id = id(individual("[" + flat("S-01") + "]", "\"SAMPLE\""));
        mvc.perform(get("/api/inspection-overview/" + sonne()).session(login("clerk")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rounds[*].id", not(hasItem((int) id))))
            .andExpect(jsonPath("$.openInspections[?(@.flat == 'S-01')].kind").value(contains("INDIVIDUAL")));
    }

    private ResultActions individual(String flatIds, String occasion) throws Exception {
        return write(post(PATH), "clerk", body("\"INDIVIDUAL\"", flatIds, occasion));
    }

    private String body(String kind, String flatIds, String occasion) {
        return """
            {"residenceId":%d,"startsOn":"%s","endsOn":"%s","windowStart":"09:00","windowEnd":"16:00",
             "caretakerId":%d,"kind":%s,"flatIds":%s,"occasion":%s}
            """.formatted(sonne(), day(10), day(12), userId("caretaker"), kind, flatIds, occasion);
    }

    private void deliverAll(long periodId) {
        entities.flush();
        entities.createQuery("select i.id from InspectionEntity i where i.period.id = :id", Long.class)
            .setParameter("id", periodId).getResultList()
            .forEach(inspectionId -> delivery.deliver(inspectionId, InspectionNotificationEntity.Kind.ANNOUNCEMENT));
        entities.flush();
    }

    private ResultActions write(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String key, String content) throws Exception {
        return mvc.perform(request.session(login(key)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private long version(long id) {
        entities.flush();
        return periods.findById(id).orElseThrow().getVersion();
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static LocalDate day(int offset) {
        return LocalDate.now(ZoneId.of("Europe/Berlin")).plusDays(offset);
    }

    private Long sonne() {
        return residences.findFirstByName("Sonnenbühl West I").orElseThrow().getId();
    }

    private Long flat(String label) {
        return flats.findByResidenceIdAndLabel(sonne(), label).orElseThrow().getId();
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
