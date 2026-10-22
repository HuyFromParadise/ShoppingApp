package clothing.app.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The browser preflights every cross-origin auth call before sending it. The
 * preflight must be answered with the allowed origin and credentials enabled,
 * otherwise the browser blocks the login request entirely.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = { "FRONTEND_ORIGIN=https://shop.example" })
class CorsPreflightTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void preflightForLoginIsAllowedWithCredentials() throws Exception {
		mockMvc.perform(options("/api/v1/auth/login")
				.header("Origin", "https://shop.example")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://shop.example"))
			.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void preflightFromAnotherOriginIsRejected() throws Exception {
		mockMvc.perform(options("/api/v1/auth/login")
				.header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type"))
			.andExpect(status().isForbidden());
	}

	@Test
	void anAllowedOriginGetsTheCorsHeadersOnAnActualRequest() throws Exception {
		mockMvc.perform(get("/api/v1/categories").header("Origin", "https://shop.example"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://shop.example"))
			.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void anActualRequestFromAnotherOriginIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/categories").header("Origin", "https://evil.example"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void theRefreshEndpointAnswersACrossSiteCallWithTheErrorEnvelope() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "https://evil.example"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}
}
