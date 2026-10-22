package clothing.app.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Reuse detection must survive the failed request: replaying a rotated refresh
 * token has to revoke the whole chain, not only report the replay.
 *
 * <p>This test is deliberately NOT {@code @Transactional}. Each request runs in
 * its own transaction, like in production. Inside a test-managed transaction
 * the revocation stays visible even when the request rolls back, which hides
 * the bug this test exists for.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "RATE_LIMIT_AUTH_CAPACITY=1000")
class RefreshReuseDetectionTest {

	private static final String EMAIL = "reuse@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@AfterEach
	void cleanUp() {
		userRepository.findByEmail(EMAIL).ifPresent(user -> {
			refreshTokenRepository.deleteAll(refreshTokenRepository.findByUserAndRevokedAtIsNull(user));
			refreshTokenRepository.deleteAll(refreshTokenRepository.findAll().stream()
				.filter(token -> token.getUser().getId().equals(user.getId()))
				.toList());
			userRepository.delete(user);
		});
	}

	private Cookie login() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content("""
				{"email":"reuse@example.com","password":"Str0ngPass!234",
				 "firstName":"Re","lastName":"Use"}"""))
			.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
				{"email":"reuse@example.com","password":"Str0ngPass!234"}"""))
			.andExpect(status().isOk()).andReturn();
		Cookie cookie = login.getResponse().getCookie("refresh_token");
		assertThat(cookie).isNotNull();
		return cookie;
	}

	@Test
	void replayingARotatedTokenRevokesTheWholeChain() throws Exception {
		Cookie firstCookie = login();

		MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andReturn();
		Cookie secondCookie = refreshed.getResponse().getCookie("refresh_token");
		assertThat(secondCookie).isNotNull();

		// Replaying the rotated token is reported as unauthenticated...
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		// ...and the token the attacker still holds must be dead as well.
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(secondCookie))
			.andExpect(status().isUnauthorized());

		UserEntity user = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(refreshTokenRepository.findByUserAndRevokedAtIsNull(user)).isEmpty();
	}
}
