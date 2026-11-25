package de.seezeit.aume.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aume-attachment-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AttachmentTests {
    private static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) throws IOException {
        uploads = Files.createTempDirectory("aume-attachment-tests");
        registry.add("aume.storage.location", uploads::toString);
    }

    @AfterAll
    static void removeUploads() throws IOException {
        if (uploads != null) FileSystemUtils.deleteRecursively(uploads);
    }

    @Autowired private MockMvc mvc;
    @Value("${aume.storage.location}") private String storageLocation;

    @BeforeEach
    void clearUploads() throws IOException {
        try (var stored = Files.list(uploads)) {
            for (var file : stored.toList()) Files.deleteIfExists(file);
        }
    }

    @Test
    void authenticatedUserStoresAndReadsOwnFileUnderAnOpaqueName() throws Exception {
        var session = login("student");
        var file = new MockMultipartFile("file", "../secret.txt", "text/plain", "hello".getBytes());
        var id = mvc.perform(multipart("/api/attachments").file(file).session(session).with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", startsWith("/api/attachments/")))
            .andExpect(jsonPath("$.contentType").value("text/plain"))
            .andExpect(jsonPath("$.sizeBytes").value(5))
            .andExpect(jsonPath("$.storageKey").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        var attachmentId = JsonPath.read(id, "$.id").toString();

        mvc.perform(get("/api/attachments/" + attachmentId).session(session))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_PLAIN))
            .andExpect(content().bytes("hello".getBytes()))
            .andExpect(header().string("Cache-Control", "no-store"));

        try (var stored = Files.list(Path.of(storageLocation))) {
            var files = stored.toList();
            assertThat(files).hasSize(1);
            assertThat(files.get(0).getFileName().toString())
                .matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
            assertThat(Files.readString(files.get(0))).isEqualTo("hello");
        }
    }

    @Test
    void otherUsersAndAnonymousRequestsCannotReadOrStore() throws Exception {
        var owner = login("student");
        var file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        var body = mvc.perform(multipart("/api/attachments").file(file).session(owner).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var attachmentId = JsonPath.read(body, "$.id").toString();

        mvc.perform(get("/api/attachments/" + attachmentId).session(login("caretaker")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("not_found"));
        mvc.perform(get("/api/attachments/" + attachmentId)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/attachments").file(file).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/attachments").file(file).session(owner))
            .andExpect(status().isForbidden());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        var file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        mvc.perform(multipart("/api/attachments").file(file).session(login("student")).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("empty"));
        try (var stored = Files.list(Path.of(storageLocation))) {
            assertThat(stored.toList()).isEmpty();
        }
    }

    private MockHttpSession login(String key) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/dev-login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"" + key + "\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
