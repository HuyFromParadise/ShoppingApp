package clothing.app.domain.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Bucket4j rate limiting on /auth/register and /auth/login (per-IP).
 * Capacity is forced down to 2 so the third request gets 429.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = { "RATE_LIMIT_AUTH_CAPACITY=2", "RATE_LIMIT_AUTH_REFILL_MINUTES=60" })
class AuthRateLimitTest {

	@Autowired
	private MockMvc mockMvc;

	private static final String BODY = "{\"email\":\"a@example.com\"}";

	@Test
	void fourthAuthRequestIsRateLimited() throws Exception {
		for (int i = 0; i < 2; i++) {
			// bucket counts the attempt before validation; an invalid body still consumes a token
			mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(BODY))
				.andExpect(status().isBadRequest());
		}
		mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(BODY))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}
}
