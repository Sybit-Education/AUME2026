package de.seezeit.aume;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listsUsersAsAPage() throws Exception {
		mockMvc.perform(get("/api/users").param("page", "0").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page.size").value(2))
				.andExpect(jsonPath("$.page.totalElements").value(3))
				.andExpect(jsonPath("$._embedded.users.length()").value(2));
	}

	@Test
	void studentReceivesAGreeting() throws Exception {
		mockMvc.perform(get("/api/greeting").header("X-Dev-User", "student"))
				.andExpect(status().isOk())
				.andExpect(content().string("Hallo Studierende, willkommen in Wohnanlage Sonne."));
	}

	@Test
	void otherRolesHaveNoGreeting() throws Exception {
		mockMvc.perform(get("/api/greeting").header("X-Dev-User", "caretaker"))
				.andExpect(status().isForbidden());
	}

}
