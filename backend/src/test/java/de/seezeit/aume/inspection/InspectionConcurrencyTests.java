package de.seezeit.aume.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gleichzeitige Änderungen an derselben Kontrolle. Bewusst ohne {@code @Transactional}, damit jede
 * Anfrage in einer eigenen Transaktion läuft und die Sperre auf der Kontrolle tatsächlich greift.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-inspection-concurrency-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InspectionConcurrencyTests {
    private static final int THREADS = 8;

    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private FindingRepository findings;
    @Autowired private TransactionTemplate tx;

    @Test
    void parallelRatingsOfTheSameCriterionCreateOneFinding() throws Exception {
        var session = login("caretaker");
        Long id = newInspection();
        Integer criterion = start(session, id).get(0);

        List<Integer> statuses = parallel(THREADS, i -> mvc.perform(
                put("/api/inspections/" + id + "/findings/" + criterion).session(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"OK\"}"))
            .andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200);
        assertThat(findings.findByInspectionId(id)).hasSize(1);
    }

    @Test
    void parallelMarkOkAndRatingDoNotCollide() throws Exception {
        var session = login("caretaker");
        Long id = newInspection();
        Integer criterion = start(session, id).get(0);

        List<Integer> statuses = parallel(THREADS, i -> (i % 2 == 0
                ? mvc.perform(post("/api/inspections/" + id + "/mark-ok").session(session).with(csrf()))
                : mvc.perform(put("/api/inspections/" + id + "/findings/" + criterion).session(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"OK\"}")))
            .andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200);
        assertThat(findings.findByInspectionId(id)).hasSize(10);
    }

    @Test
    void finalizeNeverRacesPastAClearedFinding() throws Exception {
        var session = login("caretaker");
        for (int round = 0; round < 5; round++) {
            Long id = newInspection();
            Integer criterion = start(session, id).get(0);
            mvc.perform(post("/api/inspections/" + id + "/mark-ok").session(session).with(csrf()))
                .andExpect(jsonPath("$.findings.length()").value(10));

            parallel(2, i -> (i == 0
                    ? mvc.perform(delete("/api/inspections/" + id + "/findings/" + criterion)
                        .session(session).with(csrf()))
                    : mvc.perform(post("/api/inspections/" + id + "/finalize").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"overallResult\":\"PASSED\"}")))
                .andReturn().getResponse().getStatus());

            var status = inspections.findById(id).orElseThrow().getStatus();
            if (status == InspectionEntity.Status.FINALIZED) {
                assertThat(findings.findByInspectionId(id))
                    .as("finalized inspection must have every criterion rated").hasSize(10);
            }
        }
    }

    private interface Request {
        int run(int index) throws Exception;
    }

    /** Startet alle Anfragen gleichzeitig und liefert ihre HTTP-Status. */
    private List<Integer> parallel(int count, Request request) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        try {
            var ready = new CountDownLatch(count);
            var go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                Callable<Integer> task = () -> {
                    ready.countDown();
                    go.await();
                    return request.run(index);
                };
                results.add(pool.submit(task));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) {
                statuses.add(result.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Eigene PLANNED-Kontrolle je Durchlauf, damit sich die Tests keinen Zustand teilen. */
    private Long newInspection() {
        return tx.execute(status -> {
            var template = inspections.findAll().stream()
                .filter(i -> i.getFlat().getLabel().equals("4.12")).findFirst().orElseThrow();
            return inspections.save(new InspectionEntity(template.getFlat(), null)).getId();
        });
    }

    private List<Integer> start(MockHttpSession session, Long id) throws Exception {
        String started = mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(started, "$.criteria[*].id");
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
