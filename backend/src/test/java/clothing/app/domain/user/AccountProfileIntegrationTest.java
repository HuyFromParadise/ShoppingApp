package clothing.app.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile contract: the customer comes from the access token, only the
 * contact fields are writable and the email is not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "RATE_LIMIT_AUTH_CAPACITY=1000")
@Transactional
class AccountProfileIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass!234\","
					+ "\"firstName\":\"An\",\"lastName\":\"Nguyen\",\"phone\":\"0900000000\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return com.jayway.jsonpath.JsonPath.read(
			result.getResponse().getContentAsString(), "$.data.accessToken");
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/account/profile"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/v1/account/profile")
				.contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"X\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsTheProfileOfTheSignedInCustomer() throws Exception {
		String token = register("profile@example.com");

		mockMvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.email").value("profile@example.com"))
			.andExpect(jsonPath("$.data.firstName").value("An"))
			.andExpect(jsonPath("$.data.lastName").value("Nguyen"))
			.andExpect(jsonPath("$.data.phone").value("0900000000"))
			.andExpect(jsonPath("$.data.role").value("CUSTOMER"));
	}

	@Test
	void updatesTheContactFields() throws Exception {
		String token = register("update-profile@example.com");

		mockMvc.perform(patch("/api/v1/account/profile")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"  An  \",\"lastName\":\"Nguyen Thi\",\"phone\":\"0912345678\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.firstName").value("An"))
			.andExpect(jsonPath("$.data.lastName").value("Nguyen Thi"))
			.andExpect(jsonPath("$.data.phone").value("0912345678"));
	}

	@Test
	void keepsUnsentFieldsUnchanged() throws Exception {
		String token = register("partial@example.com");

		mockMvc.perform(patch("/api/v1/account/profile")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"An Pham\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.firstName").value("An Pham"))
			.andExpect(jsonPath("$.data.lastName").value("Nguyen"))
			.andExpect(jsonPath("$.data.phone").value("0900000000"));
	}

	@Test
	void clearsThePhoneWhenAnEmptyStringIsSent() throws Exception {
		String token = register("clear-phone@example.com");

		mockMvc.perform(patch("/api/v1/account/profile")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.phone").doesNotExist());
	}

	@Test
	void doesNotLetTheEmailBeChanged() throws Exception {
		String token = register("email@example.com");

		mockMvc.perform(patch("/api/v1/account/profile")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"hijack@example.com\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.email").value("email@example.com"));

		assertThat(userRepository.findByEmail("hijack@example.com")).isEmpty();
	}

	@Test
	void refusesToBlankTheName() throws Exception {
		String token = register("blank-name@example.com");

		mockMvc.perform(patch("/api/v1/account/profile")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"   \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsInvalidValues() throws Exception {
		String token = register("invalid-profile@example.com");

		mockMvc.perform(patch("/api/v1/account/profile")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"\",\"phone\":\"0123456789012345678901234567890123456789\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.firstName").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.phone").isNotEmpty());
	}
}
