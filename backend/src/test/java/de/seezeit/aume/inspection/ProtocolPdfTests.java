package de.seezeit.aume.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** PDF-Download des Protokolls (#11). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-protocol-pdf-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ProtocolPdfTests {
    @Autowired private MockMvc mvc;
    @Autowired private InspectionRepository inspections;
    @Autowired private ResidenceRepository residences;
    @Autowired private FlatRepository flats;

    @Test
    void onlyCaretakersMayDownloadTheProtocol() throws Exception {
        String path = pdfOf(failed());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String key : new String[]{"student", "clerk"}) {
            mvc.perform(get(path).session(login(key))).andExpect(status().isForbidden());
        }
    }

    @Test
    void protocolContainsTheRecordedInspection() throws Exception {
        var inspection = failed();
        inspection.setInspectedAt(LocalDateTime.parse("2026-10-25T09:15").atZone(ZoneId.of("Europe/Berlin")).toInstant());

        var response = mvc.perform(get(pdfOf(inspection)).session(login("caretaker")))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"Kontrollprotokoll_S-01_2026-10-25.pdf\""))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andReturn().getResponse();

        var text = text(response.getContentAsByteArray());
        assertThat(text).contains("Kontrollprotokoll", "Wohnanlage Sonne", "S-01", "Erstkontrolle",
            "25.10.2026", "Nachkontrolle erforderlich",
            "Herd & Backofen", "Schwer", "Eingebrannte Fettschicht",
            "Mülltrennung", "Vor Ort geklärt", "Abfluss vor Ort gereinigt.");
        // Plätze gehören nicht ins Protokoll, und geprüft wird nur mit Datum, ohne Uhrzeit.
        assertThat(text).doesNotContain("Plätze", "25.10.2026, 09:15");
    }

    @Test
    void protocolFollowsTheLanguageOfTheUser() throws Exception {
        var response = mvc.perform(get(pdfOf(failed())).session(login("caretaker"))
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
            .andExpect(status().isOk())
            .andReturn().getResponse();
        assertThat(text(response.getContentAsByteArray()))
            .contains("Inspection report", "First inspection", "Follow-up required", "Severe");
    }

    @Test
    void openInspectionsHaveNoProtocolYet() throws Exception {
        var planned = inspections.findAll().stream()
            .filter(i -> i.getStatus() == InspectionEntity.Status.PLANNED).findFirst().orElseThrow();
        mvc.perform(get(pdfOf(planned)).session(login("caretaker"))).andExpect(status().isConflict());
    }

    @Test
    void protocolsOfOtherResidencesAreInvisible() throws Exception {
        var other = residences.save(new ResidenceEntity("Andere Wohnanlage"));
        var foreign = new InspectionEntity(flats.save(new FlatEntity(other, "1.01", 2)), null);
        foreign.setStatus(InspectionEntity.Status.FINALIZED);
        foreign.setOverallResult(InspectionEntity.Result.PASSED);
        foreign.setInspectedAt(Instant.now());
        inspections.save(foreign);

        var session = login("caretaker");
        mvc.perform(get(pdfOf(foreign)).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/inspections/999999/protocol.pdf").session(session)).andExpect(status().isNotFound());
    }

    private InspectionEntity failed() {
        return inspections.findAll().stream()
            .filter(i -> InspectionHistoryService.DONE.contains(i.getStatus())
                && i.getOverallResult() == InspectionEntity.Result.FAILED)
            .findFirst().orElseThrow();
    }

    private static String pdfOf(InspectionEntity inspection) {
        return "/api/inspections/" + inspection.getId() + "/protocol.pdf";
    }

    private static String text(byte[] pdf) throws Exception {
        try (var reader = new PdfReader(pdf)) {
            var extractor = new PdfTextExtractor(reader);
            var text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        }
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
