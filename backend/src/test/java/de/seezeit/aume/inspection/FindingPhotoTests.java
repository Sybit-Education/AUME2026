package de.seezeit.aume.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * US-35: Foto zum Mangel. Startdaten: caretaker betreut Sonne; caretaker2 betreut Petershausen und vertritt Sonne.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-finding-photo-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class FindingPhotoTests {
    private static final byte[] JPEG = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);

    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;

    @Test
    void caretakerAttachesAPhotoAndTheDeputySeesIt() throws Exception {
        var caretaker = login("caretaker");
        var draft = draftWithDefect(caretaker, "Wohnanlage Sonne");
        String added = addPhoto(caretaker, draft, upload(caretaker, "image/jpeg", JPEG))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(photoCount(added, draft.criterion())).isEqualTo(1);
        long photoId = photoIdOf(added, draft.criterion());

        mvc.perform(get(photoPath(draft, photoId)).session(caretaker))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(content().bytes(JPEG));
        mvc.perform(get(photoPath(draft, photoId)).session(login("caretaker2")))
            .andExpect(status().isOk())
            .andExpect(content().bytes(JPEG));
        mvc.perform(get(photoPath(draft, photoId)).session(login("student"))).andExpect(status().isForbidden());
    }

    @Test
    void photosOfOtherResidencesStayInvisible() throws Exception {
        var deputy = login("caretaker2");
        var draft = draftWithDefect(deputy, "Petershausen");
        long photoId = photoIdOf(addPhoto(deputy, draft, upload(deputy, "image/png", JPEG))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), draft.criterion());

        // caretaker ist nur für Sonne zuständig.
        mvc.perform(get(photoPath(draft, photoId)).session(login("caretaker"))).andExpect(status().isNotFound());
    }

    @Test
    void onlyOwnImagesCanBeAttachedOnceToADefect() throws Exception {
        var caretaker = login("caretaker");
        var draft = draftWithDefect(caretaker, "Wohnanlage Sonne");

        addPhoto(caretaker, draft, upload(login("caretaker2"), "image/jpeg", JPEG)).andExpect(status().isNotFound());
        addPhoto(caretaker, draft, upload(caretaker, "application/pdf", JPEG)).andExpect(status().isBadRequest());
        // SVG kann Skripte enthalten und ist trotz image/ kein erlaubtes Foto.
        addPhoto(caretaker, draft, upload(caretaker, "image/svg+xml", "<svg><script>alert(1)</script></svg>"
            .getBytes(StandardCharsets.UTF_8))).andExpect(status().isBadRequest());
        // Groß geschriebene Typen gelten wie klein geschriebene.
        addPhoto(caretaker, draft, upload(caretaker, "IMAGE/PNG", JPEG)).andExpect(status().isOk());
        long attachment = upload(caretaker, "image/jpeg", JPEG);
        addPhoto(caretaker, draft, attachment).andExpect(status().isOk());
        addPhoto(caretaker, draft, attachment).andExpect(status().isConflict());

        // Ein Kriterium ohne Mangel bekommt kein Foto.
        mvc.perform(put(findingPath(draft.id(), draft.okCriterion())).session(caretaker).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"OK\"}"))
            .andExpect(status().isOk());
        mvc.perform(post(findingPath(draft.id(), draft.okCriterion()) + "/photos").session(caretaker).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentId\":" + upload(caretaker, "image/jpeg", JPEG) + "}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void atMostFivePhotosPerDefect() throws Exception {
        var caretaker = login("caretaker");
        var draft = draftWithDefect(caretaker, "Wohnanlage Sonne");
        for (int i = 0; i < InspectionService.MAX_PHOTOS; i++) {
            addPhoto(caretaker, draft, upload(caretaker, "image/jpeg", JPEG)).andExpect(status().isOk());
        }
        addPhoto(caretaker, draft, upload(caretaker, "image/jpeg", JPEG)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("too_many_photos"));
    }

    @Test
    void removingAPhotoOrTheDefectCleansUp() throws Exception {
        var caretaker = login("caretaker");
        var draft = draftWithDefect(caretaker, "Wohnanlage Sonne");
        long first = photoIdOf(addPhoto(caretaker, draft, upload(caretaker, "image/jpeg", JPEG))
            .andReturn().getResponse().getContentAsString(), draft.criterion());
        String removed = mvc.perform(delete(findingPath(draft.id(), draft.criterion()) + "/photos/" + first)
                .session(caretaker).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(photoCount(removed, draft.criterion())).isZero();
        mvc.perform(get(photoPath(draft, first)).session(caretaker)).andExpect(status().isNotFound());

        long second = photoIdOf(addPhoto(caretaker, draft, upload(caretaker, "image/jpeg", JPEG))
            .andReturn().getResponse().getContentAsString(), draft.criterion());
        mvc.perform(delete(findingPath(draft.id(), draft.criterion())).session(caretaker).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(get(photoPath(draft, second)).session(caretaker)).andExpect(status().isNotFound());

        // Wird aus dem Mangel ein OK, gehören die Fotos nicht mehr dazu.
        rateDeficient(caretaker, draft.id(), draft.criterion());
        long third = photoIdOf(addPhoto(caretaker, draft, upload(caretaker, "image/jpeg", JPEG))
            .andReturn().getResponse().getContentAsString(), draft.criterion());
        mvc.perform(put(findingPath(draft.id(), draft.criterion())).session(caretaker).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"OK\"}"))
            .andExpect(status().isOk());
        mvc.perform(get(photoPath(draft, third)).session(caretaker)).andExpect(status().isNotFound());
    }

    @Test
    void photosStayVisibleButLockedAfterFinishing() throws Exception {
        var caretaker = login("caretaker");
        var draft = draftWithDefect(caretaker, "Wohnanlage Sonne");
        long photoId = photoIdOf(addPhoto(caretaker, draft, upload(caretaker, "image/jpeg", JPEG))
            .andReturn().getResponse().getContentAsString(), draft.criterion());
        mvc.perform(post("/api/inspections/" + draft.id() + "/mark-ok").session(caretaker).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/inspections/" + draft.id() + "/finalize").session(caretaker).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"overallResult\":\"FAILED\"}"))
            .andExpect(status().isOk());

        mvc.perform(get(photoPath(draft, photoId)).session(caretaker)).andExpect(status().isOk());
        mvc.perform(delete(findingPath(draft.id(), draft.criterion()) + "/photos/" + photoId).session(caretaker).with(csrf()))
            .andExpect(status().isConflict());
        addPhoto(caretaker, draft, upload(caretaker, "image/jpeg", JPEG)).andExpect(status().isConflict());
    }

    private record Draft(long id, long criterion, long okCriterion) {}

    /**
     * Legt in der Anlage eine eigene geplante Kontrolle ohne Zeitraum an (unabhängig von den gemeinsamen
     * Startdaten), startet sie und erfasst einen Mangel am ersten Kriterium.
     */
    private Draft draftWithDefect(MockHttpSession session, String residenceName) throws Exception {
        var residence = residences.findFirstByName(residenceName).orElseThrow();
        var flat = flats.save(new FlatEntity(residence, "F-" + System.nanoTime(), 3));
        long id = inspections.save(new InspectionEntity(flat, null)).getId();
        String started = mvc.perform(post("/api/inspections/" + id + "/start").session(session).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> criteria = JsonPath.read(started, "$.criteria[*].id");
        rateDeficient(session, id, criteria.get(0));
        return new Draft(id, criteria.get(0), criteria.get(1));
    }

    private void rateDeficient(MockHttpSession session, long id, long criterion) throws Exception {
        mvc.perform(put(findingPath(id, criterion)).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"DEFICIENT\",\"severity\":\"LIGHT\",\"note\":\"Fettspritzer\"}"))
            .andExpect(status().isOk());
    }

    private long upload(MockHttpSession session, String contentType, byte[] bytes) throws Exception {
        String body = mvc.perform(multipart("/api/attachments")
                .file(new MockMultipartFile("file", "foto", contentType, bytes)).session(session).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions addPhoto(MockHttpSession session, Draft draft,
            long attachmentId) throws Exception {
        return mvc.perform(post(findingPath(draft.id(), draft.criterion()) + "/photos").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"attachmentId\":" + attachmentId + "}"));
    }

    private static long photoIdOf(String body, long criterion) {
        List<Number> ids = JsonPath.read(body, "$.findings[?(@.criterionId == " + criterion + ")].photoIds[-1]");
        return ids.getFirst().longValue();
    }

    private static int photoCount(String body, long criterion) {
        List<List<Object>> lists = JsonPath.read(body, "$.findings[?(@.criterionId == " + criterion + ")].photoIds");
        return lists.getFirst().size();
    }

    private static String findingPath(long id, long criterion) {
        return "/api/inspections/" + id + "/findings/" + criterion;
    }

    private static String photoPath(Draft draft, long photoId) {
        return "/api/inspections/" + draft.id() + "/photos/" + photoId;
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
