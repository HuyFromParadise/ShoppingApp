package clothing.app.domain.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.security.JwtService;
import clothing.app.domain.address.AddressRepository;
import clothing.app.domain.cart.CartItemRepository;
import clothing.app.domain.cart.CartRepository;
import clothing.app.domain.order.OrderWorkflowFixture;
import clothing.app.domain.order.OrderWorkflowFixture.Customer;
import clothing.app.domain.user.UserRepository;

/**
 * Promotions end to end (PROJECT_SPEC §7): the admin creates a code, the
 * checkout quote applies it from the server's own subtotal, and the saved order
 * carries the discounted total. A bad code is refused with the reason.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=1000",
	"app.orders.timeout-sweep-interval-ms=86400000"})
@Transactional
class PromotionIntegrationTest {

	private static final long SUBTOTAL = OrderWorkflowFixture.BASE_PRICE * 2;
	private static final long SHIPPING = OrderWorkflowFixture.SHIPPING_FEE;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private AddressRepository addressRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ProductVariantRepository variantRepository;
	@Autowired
	private ProductImageRepository imageRepository;
	@Autowired
	private CartRepository cartRepository;
	@Autowired
	private CartItemRepository cartItemRepository;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private EntityManager entityManager;

	private OrderWorkflowFixture fixture;
	private String adminToken;

	@BeforeEach
	void setUp() {
		fixture = new OrderWorkflowFixture(mockMvc, jdbcTemplate, userRepository,
			passwordEncoder, addressRepository, categoryRepository, productRepository,
			variantRepository, imageRepository, jwtService, entityManager);
		adminToken = fixture.tokenOf(fixture.newAdmin("promo-admin"));
	}

	@Test
	void theAdminCreatesACodeThatTheQuoteThenApplies() throws Exception {
		createPromotion("{\"code\":\"save10\",\"type\":\"PERCENTAGE\",\"value\":10}")
			.andExpect(status().isCreated())
			// stored upper-cased, and the spec's PERCENTAGE maps to the schema's PERCENT
			.andExpect(jsonPath("$.data.code").value("SAVE10"))
			.andExpect(jsonPath("$.data.type").value("PERCENT"))
			.andExpect(jsonPath("$.data.active").value(true));

		Customer customer = customerWithCart("promo-apply", 2);
		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + fixture.tokenOf(customer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(customer, "  save10 ")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.subtotalMinor").value(SUBTOTAL))
			.andExpect(jsonPath("$.data.discountTotalMinor").value(SUBTOTAL / 10))
			.andExpect(jsonPath("$.data.grandTotalMinor")
				.value(SUBTOTAL - SUBTOTAL / 10 + SHIPPING))
			.andExpect(jsonPath("$.data.promotionCode").value("SAVE10"));
	}

	@Test
	void theOrderStoresTheServerComputedDiscount() throws Exception {
		createPromotion("{\"code\":\"FIXED50K\",\"type\":\"FIXED_AMOUNT\",\"value\":50000}")
			.andExpect(status().isCreated());

		Customer customer = customerWithCart("promo-order", 2);
		String token = fixture.tokenOf(customer);
		MvcResult created = mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.header("Idempotency-Key", "promo-order-key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":\"" + customer.addressId()
					+ "\",\"shippingMethod\":\"STANDARD\",\"promotionCode\":\"fixed50k\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.discountTotalMinor").value(50000))
			.andExpect(jsonPath("$.data.grandTotalMinor").value(SUBTOTAL - 50000 + SHIPPING))
			.andReturn();

		String orderId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject(
			"select discount_total from orders where id = ?", Long.class, orderId))
			.isEqualTo(50000);
	}

	@Test
	void aBadCodeIsRefusedWithAReasonAndNeverSilentlyIgnored() throws Exception {
		Customer customer = customerWithCart("promo-bad", 1);
		String token = fixture.tokenOf(customer);

		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(customer, "NOPE")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.promotionCode").isNotEmpty());

		// an order for a bad code must not be created either
		mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.header("Idempotency-Key", "promo-bad-key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":\"" + customer.addressId()
					+ "\",\"shippingMethod\":\"STANDARD\",\"promotionCode\":\"NOPE\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void aDeactivatedCodeStopsWorking() throws Exception {
		MvcResult created = createPromotion(
			"{\"code\":\"LIMITED\",\"type\":\"PERCENT\",\"value\":20}")
			.andExpect(status().isCreated())
			.andReturn();
		String promotionId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(patch("/api/v1/admin/promotions/" + promotionId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.active").value(false));

		Customer customer = customerWithCart("promo-deactivated", 1);
		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + fixture.tokenOf(customer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(customer, "LIMITED")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.promotionCode").value(
				org.hamcrest.Matchers.containsString("no longer available")));
	}

	@Test
	void refusesAnAdminPromotionPayloadItCannotHonour() throws Exception {
		// a duplicate code, an over-100 percentage and a backwards window
		createPromotion("{\"code\":\"DUP\",\"type\":\"PERCENT\",\"value\":5}")
			.andExpect(status().isCreated());
		createPromotion("{\"code\":\"dup\",\"type\":\"FIXED\",\"value\":1000}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.code").isNotEmpty());

		createPromotion("{\"code\":\"TOOBIG\",\"type\":\"PERCENTAGE\",\"value\":101}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.value").isNotEmpty());

		createPromotion("{\"code\":\"BACKWARDS\",\"type\":\"FIXED\",\"value\":1000,"
			+ "\"startsAt\":\"2026-12-01T00:00:00Z\",\"endsAt\":\"2026-11-01T00:00:00Z\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.endsAt").isNotEmpty());

		// a fixed amount above 100 is legitimate and must not be rejected
		createPromotion("{\"code\":\"BIGFIXED\",\"type\":\"FIXED\",\"value\":500000}")
			.andExpect(status().isCreated());
	}

	@Test
	void listsPromotionsForTheAdmin() throws Exception {
		createPromotion("{\"code\":\"LISTED\",\"type\":\"PERCENT\",\"value\":5}")
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/admin/promotions")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.code == 'LISTED')]").exists());
	}

	/* --- helpers -------------------------------------------------------------- */

	private org.springframework.test.web.servlet.ResultActions createPromotion(String body)
			throws Exception {
		return mockMvc.perform(post("/api/v1/admin/promotions")
			.header("Authorization", "Bearer " + adminToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/** A customer whose cart holds {@code quantity} units of one variant. */
	private Customer customerWithCart(String hint, int quantity) throws Exception {
		Customer customer = fixture.newCustomer(hint, quantity + 1);
		fixture.addToCart(fixture.tokenOf(customer), customer.variantId(), quantity);
		return customer;
	}

	private static String quoteBody(Customer customer, String code) {
		return "{\"addressId\":\"" + customer.addressId() + "\","
			+ "\"shippingMethod\":\"STANDARD\",\"promotionCode\":\"" + code + "\"}";
	}
}
