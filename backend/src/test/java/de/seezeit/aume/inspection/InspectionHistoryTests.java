package de.seezeit.aume.inspection;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.standard.ChecklistVersionEntity;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.user.CaretakerEntity;
import de.seezeit.aume.user.RoleRepository;
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
import org.springframework.transaction.annotation.Transactional;

/** Kontroll-Historie (#40, #42) und Detailansicht abgeschlossener Kontrollen (#43). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-inspection-history-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InspectionHistoryTests {
    private static final String HISTORY = "/api/inspections/history";

    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;
    @Autowired private EntityManager entities;
    @Autowired private AppUserService users;
    @Autowired private RoleRepository roles;

    private CaretakerEntity caretaker;
    private ChecklistVersionEntity checklist;
    private InspectionEntity initial;
    private InspectionEntity followUp;
    private InspectionEntity otherFlatInspection;
    private InspectionEntity planned;

    @BeforeEach
    void createHistoryFixtures() {
        var residence = residences.save(new ResidenceEntity("Historie-Testwohnanlage"));
        var flat = flats.save(new FlatEntity(residence, "S-01", 4));
        var otherFlat = flats.save(new FlatEntity(residence, "S-02", 3));
        caretaker = new CaretakerEntity("history-test-caretaker", "Historie-Testperson",
            Set.of(roles.findByName("CARETAKER").orElseThrow()), residence);
        entities.persist(caretaker);

        // Select this published checklist when the test starts a planned inspection.
        checklist = new ChecklistVersionEntity(Integer.MAX_VALUE, caretaker);
        checklist.setStatus(ChecklistVersionEntity.Status.PUBLISHED);
        checklist.setPublishedAt(Instant.parse("2020-01-01T08:00:00Z"));
        entities.persist(checklist);
        initial = published(new InspectionEntity(flat, null), "2020-01-02T09:00:00Z", InspectionEntity.Result.FAILED);
        otherFlatInspection = published(new InspectionEntity(otherFlat, null), "2020-01-04T09:00:00Z",
            InspectionEntity.Result.PASSED);
        followUp = published(new InspectionEntity(initial, null), "2020-01-09T09:00:00Z",
            InspectionEntity.Result.PASSED);
        planned = inspections.save(new InspectionEntity(otherFlat, null));

        for (int position = 1; position <= 10; position++) {
            var criterion = new CriterionEntity(checklist, "Küche", "Kriterium " + position, "Testbeschreibung", position);
            entities.persist(criterion);
            var rating = position <= 2 ? FindingEntity.Rating.DEFICIENT : FindingEntity.Rating.OK;
            var finding = new FindingEntity(initial, criterion, rating);
            if (rating == FindingEntity.Rating.DEFICIENT) {
                finding.setSeverity(FindingEntity.Severity.SEVERE);
                finding.setNote("Mangel an Kriterium " + position);
            }
            entities.persist(finding);
        }
    }

    private InspectionEntity published(InspectionEntity inspection, String date, InspectionEntity.Result result) {
        var timestamp = Instant.parse(date);
        inspection.setChecklistVersion(checklist);
        inspection.setInspector(caretaker);
        inspection.setInspectedAt(timestamp);
        inspection.setOverallResult(result);
        inspection.setStatus(InspectionEntity.Status.PUBLISHED);
        inspection.setPublishedBy(caretaker);
        inspection.setPublishedAt(timestamp);
        return inspections.save(inspection);
    }

    private InspectionEntity additionalInitial() {
        return published(new InspectionEntity(initial.getFlat(), null), "2026-04-04T09:00:00Z",
            InspectionEntity.Result.FAILED);
    }

    @Test
    void onlyCaretakersMaySeeTheHistory() throws Exception {
        mvc.perform(get(HISTORY)).andExpect(status().isUnauthorized());
        for (String key : new String[]{"student", "clerk"}) {
            mvc.perform(get(HISTORY).session(login(key))).andExpect(status().isForbidden());
        }
    }

    @Test
    void historyListsFinishedInspectionsNewestFirst() throws Exception {
        var additional = additionalInitial();
        mvc.perform(get(HISTORY).session(caretakerSession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(4))
            .andExpect(jsonPath("$.content[0].id").value(additional.getId().intValue()))
            .andExpect(jsonPath("$.content[1].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.content[2].id").value(otherFlatInspection.getId().intValue()))
            .andExpect(jsonPath("$.content[3].id").value(initial.getId().intValue()))
            .andExpect(jsonPath("$.content[0].flatLabel").value("S-01"))
            .andExpect(jsonPath("$.content[0].round").value(0))
            .andExpect(jsonPath("$.content[0].overallResult").value("FAILED"))
            .andExpect(jsonPath("$.content[1].flatLabel").value("S-01"))
            .andExpect(jsonPath("$.content[1].round").value(1))
            .andExpect(jsonPath("$.content[1].overallResult").value("PASSED"))
            .andExpect(jsonPath("$.content[2].flatLabel").value("S-02"))
            .andExpect(jsonPath("$.content[3].overallResult").value("FAILED"))
            .andExpect(jsonPath("$.content[3].deficiencies").value(2))
            .andExpect(jsonPath("$.content[3].inspectorName").isNotEmpty())
            .andExpect(jsonPath("$.content[3].status").value("PUBLISHED"));
    }

    @Test
    void historyCanBeFiltered() throws Exception {
        additionalInitial();
        var session = caretakerSession();
        expectTotal(session, "?flat=S-01", 3);
        expectTotal(session, "?flat=01", 3);
        expectTotal(session, "?result=FAILED", 2);
        expectTotal(session, "?followUp=true", 1);
        expectTotal(session, "?followUp=false", 3);
        expectTotal(session, "?flat=S-01&followUp=false&result=FAILED", 2);
    }

    @Test
    void periodFilterUsesBerlinCalendarDays() throws Exception {
        // Feste Prüfdaten statt "vor N Tagen": Sonst hängt das Ergebnis von Uhrzeit und Zeitumstellung
        // des Testlaufs ab. Die Daten liegen bewusst um die Umstellung auf Sommerzeit am 29.03.2026.
        inspectedAt(initial, "2026-03-27T23:30");  // Erstkontrolle, spät abends in Winterzeit
        inspectedAt(otherFlatInspection, "2026-03-29T00:30");  // kurz nach Mitternacht am Tag der Umstellung
        inspectedAt(followUp, "2026-04-03T12:00");  // Nachkontrolle

        var session = caretakerSession();
        additionalInitial();
        expectTotal(session, "?from=2026-03-28", 3);
        expectTotal(session, "?to=2026-03-28", 1);
        expectTotal(session, "?from=2026-03-28&to=2026-03-28", 0);
        // beide Tage inklusive, auch für Prüfungen kurz vor bzw. nach Mitternacht
        expectTotal(session, "?from=2026-03-27&to=2026-03-27", 1);
        expectTotal(session, "?from=2026-03-29&to=2026-03-29", 1);
        expectTotal(session, "?from=2026-03-27&to=2026-04-03", 3);
    }

    private void inspectedAt(InspectionEntity inspection, String berlinLocalTime) {
        inspection.setInspectedAt(LocalDateTime.parse(berlinLocalTime).atZone(ZoneId.of("Europe/Berlin")).toInstant());
    }

    @Test
    void historyShowsTheResidenceAndFiltersByItAndByDefects() throws Exception {
        var session = login("caretaker");
        mvc.perform(get(HISTORY).session(session))
            .andExpect(jsonPath("$.content[0].residenceName").value("Wohnanlage Sonne"));
        long all = total(session, "");
        org.assertj.core.api.Assertions.assertThat(total(session, "?residence=sonne")).isEqualTo(all);
        expectTotal(session, "?residence=Egg", 0);

        // „mit Mängeln“ und „ohne Mängel“ teilen die Historie vollständig auf; die nicht bestandene Kontrolle hat Mängel.
        long with = total(session, "?defects=true");
        long without = total(session, "?defects=false");
        org.assertj.core.api.Assertions.assertThat(with + without).isEqualTo(all);
        org.assertj.core.api.Assertions.assertThat(with).isGreaterThanOrEqualTo(1);
        mvc.perform(get(HISTORY + "?defects=true&result=FAILED").session(session))
            .andExpect(jsonPath("$.content[?(@.deficiencies < 1)]").isEmpty());
        mvc.perform(get(HISTORY + "?defects=false").session(session))
            .andExpect(jsonPath("$.content[?(@.deficiencies > 0)]").isEmpty());
    }

    @Test
    void historyCanBeSortedByDefectsAndResidence() throws Exception {
        var session = login("caretaker");
        long all = total(session, "");
        List<Integer> descending = deficiencies(session, "?orderBy=DEFECTS&direction=DESC&size=100");
        List<Integer> ascending = deficiencies(session, "?orderBy=DEFECTS&direction=ASC&size=100");
        org.assertj.core.api.Assertions.assertThat(descending).hasSize((int) all).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        org.assertj.core.api.Assertions.assertThat(ascending).isSorted();
        org.assertj.core.api.Assertions.assertThat(descending.getFirst()).isGreaterThan(0);
        mvc.perform(get(HISTORY + "?orderBy=RESIDENCE").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(all));
    }

    private List<Integer> deficiencies(MockHttpSession session, String query) throws Exception {
        var body = mvc.perform(get(HISTORY + query).session(session)).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.content[*].deficiencies");
    }

    private long total(MockHttpSession session, String query) throws Exception {
        var body = mvc.perform(get(HISTORY + query).session(session)).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.page.totalElements")).longValue();
    }

    @Test
    void historyCanBeSortedByColumn() throws Exception {
        var session = caretakerSession();
        var otherInspector = new CaretakerEntity("history-test-other-inspector", "Andere Testperson",
            caretaker.getRoles(), caretaker.getResidence());
        entities.persist(otherInspector);
        initial.setInspector(otherInspector);
        // Prüfzeiten und Zuordnung stammen aus createHistoryFixtures().
        mvc.perform(get(HISTORY + "?orderBy=DATE&direction=ASC").session(session))
            .andExpect(jsonPath("$.content[0].id").value(initial.getId().intValue()))
            .andExpect(jsonPath("$.content[1].id").value(otherFlatInspection.getId().intValue()))
            .andExpect(jsonPath("$.content[2].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.content[0].round").value(0))
            .andExpect(jsonPath("$.content[2].round").value(1));
        // Bei gleicher WG: neueste zuerst
        mvc.perform(get(HISTORY + "?orderBy=FLAT").session(session))
            .andExpect(jsonPath("$.content[0].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.content[1].id").value(initial.getId().intValue()))
            .andExpect(jsonPath("$.content[2].id").value(otherFlatInspection.getId().intValue()))
            .andExpect(jsonPath("$.content[0].flatLabel").value("S-01"))
            .andExpect(jsonPath("$.content[0].round").value(1))
            .andExpect(jsonPath("$.content[1].flatLabel").value("S-01"))
            .andExpect(jsonPath("$.content[2].flatLabel").value("S-02"));
        mvc.perform(get(HISTORY + "?orderBy=FLAT&direction=DESC").session(session))
            .andExpect(jsonPath("$.content[0].id").value(otherFlatInspection.getId().intValue()))
            .andExpect(jsonPath("$.content[1].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.content[2].id").value(initial.getId().intValue()))
            .andExpect(jsonPath("$.content[0].flatLabel").value("S-02"));
        mvc.perform(get(HISTORY + "?orderBy=RESULT").session(session))
            .andExpect(jsonPath("$.content[0].id").value(initial.getId().intValue()))
            .andExpect(jsonPath("$.content[1].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.content[2].id").value(otherFlatInspection.getId().intValue()))
            .andExpect(jsonPath("$.content[0].overallResult").value("FAILED"));
        mvc.perform(get(HISTORY + "?orderBy=KIND&direction=DESC").session(session))
            .andExpect(jsonPath("$.content[0].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.content[1].id").value(otherFlatInspection.getId().intValue()))
            .andExpect(jsonPath("$.content[2].id").value(initial.getId().intValue()))
            .andExpect(jsonPath("$.content[0].round").value(1));
        mvc.perform(get(HISTORY + "?orderBy=INSPECTOR").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(initial.getId().intValue()))
            .andExpect(jsonPath("$.content[1].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.content[2].id").value(otherFlatInspection.getId().intValue()));
    }

    @Test
    void unknownSortColumnsAreRejectedAndRawSortIsIgnored() throws Exception {
        var session = caretakerSession();
        mvc.perform(get(HISTORY + "?orderBy=PASSWORD").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get(HISTORY + "?direction=UP").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get(HISTORY + "?sort=flat.residence.name,asc").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].round").value(1));
        mvc.perform(get(HISTORY + "?sort=round,asc").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(followUp.getId().intValue()))
            .andExpect(jsonPath("$.content[1].id").value(otherFlatInspection.getId().intValue()))
            .andExpect(jsonPath("$.content[2].id").value(initial.getId().intValue()));
    }

    @Test
    void likeWildcardsInTheFlatFilterAreTakenLiterally() throws Exception {
        additionalInitial();
        var session = caretakerSession();
        for (String wildcard : new String[]{"%", "_", "S_01", "S%"}) {
            mvc.perform(get(HISTORY).param("flat", wildcard).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        }
        mvc.perform(get(HISTORY).param("flat", "  s-01 ").session(session))
            .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void invalidFiltersAreRejected() throws Exception {
        var session = caretakerSession();
        mvc.perform(get(HISTORY + "?from=2026-05-02&to=2026-05-01").session(session))
            .andExpect(status().isBadRequest());
        mvc.perform(get(HISTORY + "?result=MAYBE").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get(HISTORY + "?from=gestern").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get(HISTORY + "?flat=" + "x".repeat(81)).session(session)).andExpect(status().isBadRequest());
    }

    @Test
    void finishedInspectionsOfOtherResidencesAreInvisible() throws Exception {
        additionalInitial();
        var other = residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var foreign = new InspectionEntity(flats.save(new FlatEntity(other, "1.01", 2)), null);
        foreign.setStatus(InspectionEntity.Status.FINALIZED);
        foreign.setOverallResult(InspectionEntity.Result.PASSED);
        foreign.setInspectedAt(Instant.now());
        inspections.save(foreign);

        var session = caretakerSession();
        expectTotal(session, "", 4);
        mvc.perform(get("/api/inspections/" + foreign.getId()).session(session)).andExpect(status().isNotFound());
    }

    @Test
    void detailShowsTheRecordedInspection() throws Exception {
        Long id = initial.getId();
        mvc.perform(get("/api/inspections/" + id).session(caretakerSession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.inspectedAt").isNotEmpty())
            .andExpect(jsonPath("$.inspectorName").isNotEmpty())
            .andExpect(jsonPath("$.criteria.length()").value(10))
            .andExpect(jsonPath("$.findings.length()").value(10))
            .andExpect(jsonPath("$.findings[?(@.rating == 'DEFICIENT')].note").isNotEmpty());
    }

    @Test
    void aFinalizedInspectionAppearsInTheHistory() throws Exception {
        additionalInitial();
        var session = caretakerSession();
        Long id = planned.getId();
        mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isOk());
        expectTotal(session, "", 4);
        mvc.perform(post("/api/inspections/" + id + "/mark-ok").session(session).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/inspections/" + id + "/finalize").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"overallResult\":\"PASSED\"}"))
            .andExpect(status().isOk());

        mvc.perform(get(HISTORY).session(session))
            .andExpect(jsonPath("$.page.totalElements").value(5))
            .andExpect(jsonPath("$.content[0].id").value(id))
            .andExpect(jsonPath("$.content[0].deficiencies").value(0));
    }

    private void expectTotal(MockHttpSession session, String query, int total) throws Exception {
        mvc.perform(get(HISTORY + query).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(total));
    }

    private MockHttpSession caretakerSession() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            caretaker.getUserKey(), null, users.authorities(caretaker)));
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
