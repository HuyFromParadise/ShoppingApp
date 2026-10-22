package clothing.app.domain.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
 * Address contract against the real MySQL test schema: ownership, the
 * one-default-address rule and the error envelope.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "RATE_LIMIT_AUTH_CAPACITY=1000")
@Transactional
class AddressIntegrationTest {

	private static final String ADDRESS = """
			{"recipientName":"An Nguyen","phone":"0901234567","addressLine1":"12 Le Loi",
			 "addressLine2":"","ward":"Ben Thanh","district":"District 1",
			 "cityOrProvince":"Ho Chi Minh City","postalCode":"700000"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AddressRepository addressRepository;

	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass!234\","
					+ "\"firstName\":\"An\",\"lastName\":\"Nguyen\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return com.jayway.jsonpath.JsonPath.read(
			result.getResponse().getContentAsString(), "$.data.accessToken");
	}

	private String createAddress(String token, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/account/addresses")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return com.jayway.jsonpath.JsonPath.read(
			result.getResponse().getContentAsString(), "$.data.id");
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/account/addresses"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/account/addresses")
				.contentType(MediaType.APPLICATION_JSON).content(ADDRESS))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createsAndListsOwnAddresses() throws Exception {
		String token = register("addr@example.com");

		createAddress(token, ADDRESS);

		mockMvc.perform(get("/api/v1/account/addresses").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].recipientName").value("An Nguyen"))
			.andExpect(jsonPath("$.data[0].addressLine1").value("12 Le Loi"))
			.andExpect(jsonPath("$.data[0].cityOrProvince").value("Ho Chi Minh City"))
			.andExpect(jsonPath("$.data[0].isDefault").value(false));
	}

	@Test
	void neverReturnsAnotherUsersAddresses() throws Exception {
		String ownerToken = register("owner@example.com");
		String otherToken = register("other@example.com");
		createAddress(ownerToken, ADDRESS);

		mockMvc.perform(get("/api/v1/account/addresses").header("Authorization", "Bearer " + otherToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void rejectsInvalidAddress() throws Exception {
		String token = register("invalid@example.com");

		mockMvc.perform(post("/api/v1/account/addresses")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientName\":\"\",\"addressLine1\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.recipientName").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.addressLine1").isNotEmpty());
	}

	@Test
	void keepsAtMostOneDefaultAddress() throws Exception {
		String token = register("default@example.com");

		String firstId = createAddress(token, ADDRESS.replace("}", ",\"isDefault\":true}"));
		String secondId = createAddress(token, ADDRESS.replace("12 Le Loi", "34 Nguyen Hue")
			.replace("}", ",\"isDefault\":true}"));

		List<AddressEntity> defaults = addressRepository.findByUserIdAndIsDefaultTrue(
			userIdOf(token));
		assertThat(defaults).hasSize(1);
		assertThat(defaults.get(0).getId()).isEqualTo(secondId);
		assertThat(firstId).isNotEqualTo(secondId);
	}

	@Test
	void updatesOwnAddress() throws Exception {
		String token = register("update@example.com");
		String id = createAddress(token, ADDRESS);

		mockMvc.perform(patch("/api/v1/account/addresses/" + id)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientName\":\"An Nguyet\",\"postalCode\":\"700001\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipientName").value("An Nguyet"))
			.andExpect(jsonPath("$.data.postalCode").value("700001"))
			// untouched fields stay
			.andExpect(jsonPath("$.data.addressLine1").value("12 Le Loi"));
	}

	@Test
	void refusesToBlankARequiredField() throws Exception {
		String token = register("blank@example.com");
		String id = createAddress(token, ADDRESS);

		mockMvc.perform(patch("/api/v1/account/addresses/" + id)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientName\":\"   \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		assertThat(addressRepository.findById(id).get().getRecipientName())
			.isEqualTo("An Nguyen");
	}

	@Test
	void refusesToTouchAnotherUsersAddress() throws Exception {
		String ownerToken = register("owner2@example.com");
		String otherToken = register("other2@example.com");
		String id = createAddress(ownerToken, ADDRESS);

		mockMvc.perform(patch("/api/v1/account/addresses/" + id)
				.header("Authorization", "Bearer " + otherToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientName\":\"Hacker\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(delete("/api/v1/account/addresses/" + id)
				.header("Authorization", "Bearer " + otherToken))
			.andExpect(status().isNotFound());

		// the address is untouched
		assertThat(addressRepository.findById(id)).isPresent();
		assertThat(addressRepository.findById(id).get().getRecipientName()).isEqualTo("An Nguyen");
	}

	@Test
	void deletesOwnAddress() throws Exception {
		String token = register("delete@example.com");
		String id = createAddress(token, ADDRESS);

		mockMvc.perform(delete("/api/v1/account/addresses/" + id)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());

		assertThat(addressRepository.findById(id)).isEmpty();
	}

	private String userIdOf(String token) throws Exception {
		MvcResult me = mockMvc.perform(get("/api/v1/auth/me")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return com.jayway.jsonpath.JsonPath.read(
			me.getResponse().getContentAsString(), "$.data.id");
	}
}
