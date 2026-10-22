package clothing.app.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
 * Auth contract against the real MySQL test schema (ADR-004):
 * register, login, refresh rotation with reuse detection, logout and /auth/me,
 * plus cookie attributes and the error envelope codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "RATE_LIMIT_AUTH_CAPACITY=1000")
@Transactional
class AuthIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private static final String REGISTER_BODY = """
		{"email":"A@Example.com","password":"Str0ngPass!234",
		 "firstName":"An","lastName":"Nguyen"}""";

	private String registerAndGetAccessToken() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType("application/json").content(REGISTER_BODY))
			.andExpect(status().isCreated())
			.andReturn();
		return accessToken(result);
	}

	private static String accessToken(MvcResult result) {
		try {
			return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),
				"$.data.accessToken");
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Cookie refreshCookie(MvcResult result) {
		return result.getResponse().getCookie("refresh_token");
	}

	@Test
	void registerReturnsAccessTokenAndRefreshCookie() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType("application/json").content(REGISTER_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.data.user.email").value("a@example.com"))
			.andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
			.andExpect(cookie().exists("refresh_token"))
			.andExpect(cookie().httpOnly("refresh_token", true))
			.andExpect(cookie().path("refresh_token", "/api/v1/auth"))
			.andReturn();
		// password is never echoed back
		assertThat(result.getResponse().getContentAsString()).doesNotContain("Str0ngPass");
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void registerRejectsDuplicateEmail() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(REGISTER_BODY))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(REGISTER_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void registerRejectsInvalidBody() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content("""
				{"email":"not-an-email","password":"short","firstName":"","lastName":"Nguyen"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
	}

	@Test
	void registerRejectsCommonPassword() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content("""
				{"email":"a@example.com","password":"password123","firstName":"An","lastName":"Nguyen"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
	}

	@Test
	void loginReturnsAccessTokenAndRefreshCookie() throws Exception {
		registerAndGetAccessToken();
		mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
				{"email":"a@example.com","password":"Str0ngPass!234"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.data.user.id").isNotEmpty())
			.andExpect(cookie().httpOnly("refresh_token", true));
	}

	@Test
	void loginRejectsWrongPassword() throws Exception {
		registerAndGetAccessToken();
		mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
				{"email":"a@example.com","password":"Wr0ngPass!234"}"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void loginRejectsLockedAccount() throws Exception {
		registerAndGetAccessToken();
		UserEntity user = userRepository.findAll().iterator().next();
		user.setStatus(UserEntity.Status.LOCKED);
		userRepository.save(user);
		mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
				{"email":"a@example.com","password":"Str0ngPass!234"}"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void meRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void meReturnsCurrentUser() throws Exception {
		String token = registerAndGetAccessToken();
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.email").value("a@example.com"))
			.andExpect(jsonPath("$.data.firstName").value("An"));
	}

	@Test
	void refreshRotatesTokenAndDetectsReuse() throws Exception {
		String loginToken = registerAndGetAccessToken();
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
				{"email":"a@example.com","password":"Str0ngPass!234"}"""))
			.andExpect(status().isOk()).andReturn();
		Cookie firstCookie = refreshCookie(login);
		assertThat(firstCookie).isNotNull();

		// first refresh succeeds and returns a NEW cookie
		MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andReturn();
		Cookie secondCookie = refreshCookie(refreshed);
		assertThat(secondCookie).isNotNull();
		assertThat(secondCookie.getValue()).isNotEqualTo(firstCookie.getValue());
		// the rotated access token still authenticates /me
		String newAccess = accessToken(refreshed);
		assertThat(newAccess).isNotEqualTo(loginToken);
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newAccess))
			.andExpect(status().isOk());

		// replaying the FIRST cookie is reuse detection: 401 and the chain dies
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		// even the newest cookie is now revoked
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(secondCookie))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshRejectsMissingOrInvalidCookie() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "garbage")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesTheRefreshChain() throws Exception {
		String token = registerAndGetAccessToken();
		// logout is authenticated even though it only touches the cookie
		mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
				{"email":"a@example.com","password":"Str0ngPass!234"}"""))
			.andExpect(status().isOk()).andReturn();
		Cookie cookie = refreshCookie(login);

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + token).cookie(cookie))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
			.andExpect(status().isUnauthorized());
	}
}
