package clothing.app.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSRF defense on /auth/refresh: when FRONTEND_ORIGIN is configured, a
 * cross-site Origin is rejected and the matching one is accepted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = { "FRONTEND_ORIGIN=https://shop.example", "RATE_LIMIT_AUTH_CAPACITY=1000" })
@Transactional
class AuthOriginCheckTest {

	@Autowired
	private MockMvc mockMvc;

	private Cookie login() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content("""
				{"email":"origin@example.com","password":"Str0ngPass!234",
				 "firstName":"An","lastName":"Nguyen"}"""))
			.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
				{"email":"origin@example.com","password":"Str0ngPass!234"}"""))
			.andExpect(status().isOk()).andReturn();
		assertThat(login.getResponse().getCookie("refresh_token")).isNotNull();
		return login.getResponse().getCookie("refresh_token");
	}

	@Test
	void refreshRejectsCrossSiteOrigin() throws Exception {
		Cookie cookie = login();
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header("Origin", "https://evil.example"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void refreshAllowsMatchingOrigin() throws Exception {
		Cookie cookie = login();
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header("Origin", "https://shop.example"))
			.andExpect(status().isOk());
	}
}
