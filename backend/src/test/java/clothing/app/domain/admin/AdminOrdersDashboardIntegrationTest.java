package clothing.app.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Admin order listing and dashboard (DESIGN_REFERENCE §5.3, Phase 6).
 *
 * <p>The dashboard's revenue definition is asserted, not just its shape: only
 * PAID orders count, a refunded one does not, and amounts are minor units.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=1000",
	"app.orders.timeout-sweep-interval-ms=86400000"})
@Transactional
class AdminOrdersDashboardIntegrationTest {

	static final long UNIT_PRICE = OrderWorkflowFixture.BASE_PRICE;
	static final long SHIPPING = OrderWorkflowFixture.SHIPPING_FEE;
	static final long ORDER_TOTAL = UNIT_PRICE + SHIPPING;

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
		adminToken = fixture.tokenOf(fixture.newAdmin("admin-orders"));
	}

	/* --- listing -------------------------------------------------------------- */

	@Test
	void filtersByStatusAndOrderNumber() throws Exception {
		Placed paid = placePaidOrder("filter-paid");
		Placed unpaid = placeUnpaidOrder("filter-unpaid");

		mockMvc.perform(get("/api/v1/admin/orders?status=CONFIRMED")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.id == '" + paid.orderId() + "')]").exists())
			.andExpect(jsonPath("$.data[?(@.id == '" + unpaid.orderId() + "')]").doesNotExist());

		mockMvc.perform(get("/api/v1/admin/orders?status=PENDING_PAYMENT")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.id == '" + unpaid.orderId() + "')]").exists())
			.andExpect(jsonPath("$.data[?(@.id == '" + paid.orderId() + "')]").doesNotExist());

		String number = fixture.orderNumber(paid.orderId());
		mockMvc.perform(get("/api/v1/admin/orders?q=" + number.substring(4, 10))
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].orderNumber").value(number));
	}

	@Test
	void findsOrdersByCustomerEmail() throws Exception {
		Placed order = placePaidOrder("email-search");
		String email = jdbcTemplate.queryForObject(
			"select email from users where id = ?", String.class, order.customerId());

		mockMvc.perform(get("/api/v1/admin/orders?q=" + email)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].customerEmail").value(email))
			.andExpect(jsonPath("$.data[0].itemCount").value(1))
			.andExpect(jsonPath("$.data[0].grandTotalMinor").value(ORDER_TOTAL));

		mockMvc.perform(get("/api/v1/admin/orders?q=nobody-at-all@example.invalid")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void filtersByPlacedAtWindow() throws Exception {
		Placed order = placePaidOrder("window");
		Instant placedAt = Instant.now().minusSeconds(10 * 24 * 3600);
		jdbcTemplate.update("update orders set placed_at = ? where id = ?",
			java.sql.Timestamp.from(placedAt), order.orderId());
		entityManager.clear();

		Instant from = placedAt.minusSeconds(3600);
		Instant to = placedAt.plusSeconds(3600);
		mockMvc.perform(get("/api/v1/admin/orders?placedFrom=" + from + "&placedBefore=" + to)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.id == '" + order.orderId() + "')]").exists());

		mockMvc.perform(get("/api/v1/admin/orders?placedFrom=" + to)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.id == '" + order.orderId() + "')]").doesNotExist());

		mockMvc.perform(get("/api/v1/admin/orders?placedFrom=" + to + "&placedBefore=" + from)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.placedFrom").isNotEmpty());
	}

	@Test
	void rejectsABadStatusOrTimestampInsteadOfFailing() throws Exception {
		mockMvc.perform(get("/api/v1/admin/orders?status=LOST")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.status").isNotEmpty());

		mockMvc.perform(get("/api/v1/admin/orders?placedFrom=yesterday")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.placedFrom").isNotEmpty());

		mockMvc.perform(get("/api/v1/admin/orders?page=-1")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isBadRequest());
	}

	/* --- dashboard ------------------------------------------------------------ */

	@Test
	void countsOnlySettledMoneyAsRevenue() throws Exception {
		Placed paid = placePaidOrder("revenue-paid");
		Placed unpaid = placeUnpaidOrder("revenue-unpaid");
		Placed refunded = placePaidOrder("revenue-refunded");
		mockMvc.perform(post("/api/v1/orders/" + refunded.orderId() + "/cancel")
				.header("Authorization", "Bearer " + refunded.customerToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

		MvcResult result = mockMvc.perform(get("/api/v1/admin/dashboard/summary")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.revenueLast30Days.paidOrders").value(1))
			.andExpect(jsonPath("$.data.revenueLast30Days.revenueMinor").value(ORDER_TOTAL))
			.andExpect(jsonPath("$.data.revenueLast30Days.averageOrderValueMinor")
				.value(ORDER_TOTAL))
			.andExpect(jsonPath("$.data.ordersByStatus.CONFIRMED").value(1))
			.andExpect(jsonPath("$.data.ordersByStatus.PENDING_PAYMENT").value(1))
			.andExpect(jsonPath("$.data.ordersByStatus.CANCELLED").value(1))
			.andExpect(jsonPath("$.data.lowStockThreshold").value(5))
			.andExpect(jsonPath("$.data.activeProducts").isNumber())
			.andExpect(jsonPath("$.data.draftProducts").isNumber())
			.andReturn();

		// the refunded order is excluded from revenue by design
		String body = result.getResponse().getContentAsString();
		assertThat(JsonPath.<Integer>read(body, "$.data.revenueAllTime.paidOrders")).isEqualTo(1);
		assertThat(JsonPath.<Integer>read(body, "$.data.revenueAllTime.revenueMinor"))
			.isEqualTo((int) ORDER_TOTAL);
		// paid + unpaid + cancelled were all placed in the window
		assertThat(JsonPath.<Integer>read(body, "$.data.revenueLast30Days.ordersPlaced"))
			.isGreaterThanOrEqualTo(3);
		assertThat(paid.orderId()).isNotEqualTo(unpaid.orderId());
	}

	@Test
	void listsLowStockVariantsForRestocking() throws Exception {
		Placed order = placePaidOrder("low-stock");

		mockMvc.perform(get("/api/v1/admin/dashboard/summary")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			// the fixture's variant starts at 5 and the order took 1, so it is low
			.andExpect(jsonPath("$.data.lowStock[?(@.variantId == '"
				+ order.variantId() + "')]").exists())
			.andExpect(jsonPath("$.data.lowStock[?(@.variantId == '"
				+ order.variantId() + "')].stockQuantity").value(4));
	}

	@Test
	void refusesTheDashboardToACustomer() throws Exception {
		Customer customer = fixture.newCustomer("dashboard-rbac", 1);
		mockMvc.perform(get("/api/v1/admin/dashboard/summary")
				.header("Authorization", "Bearer " + fixture.tokenOf(customer)))
			.andExpect(status().isForbidden());
	}

	/* --- helpers -------------------------------------------------------------- */

	record Placed(String orderId, String customerToken, String customerId, String variantId) {
	}

	private Placed placeUnpaidOrder(String slugHint) throws Exception {
		return place(slugHint, false);
	}

	private Placed placePaidOrder(String slugHint) throws Exception {
		return place(slugHint, true);
	}

	private Placed place(String slugHint, boolean pay) throws Exception {
		Customer customer = fixture.newCustomer(slugHint, 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, slugHint + "-idem", 1);
		if (pay) {
			MvcResult created = mockMvc.perform(post("/api/v1/payments/" + orderId + "/mock/create")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
			String paymentId = JsonPath.read(created.getResponse().getContentAsString(),
				"$.data.paymentId");
			mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));
		}
		return new Placed(orderId, token, customer.id(), customer.variantId());
	}


}
