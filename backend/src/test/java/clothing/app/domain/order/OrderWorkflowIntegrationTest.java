package clothing.app.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.security.JwtService;
import clothing.app.domain.address.AddressRepository;
import clothing.app.domain.cart.CartItemRepository;
import clothing.app.domain.cart.CartRepository;
import clothing.app.domain.order.OrderWorkflowFixture.Customer;
import clothing.app.domain.user.UserRepository;

/**
 * Order reads and cancellation (DESIGN_REFERENCE §5.3, Journey D): the
 * customer's own history, the detail with snapshots and history, ownership
 * isolation, pagination limits, and the cancel rules with restock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=1000",
	"app.orders.timeout-sweep-interval-ms=3600000"})
@Transactional
class OrderWorkflowIntegrationTest {

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

	@BeforeEach
	void setUp() {
		fixture = new OrderWorkflowFixture(mockMvc, jdbcTemplate, userRepository,
			passwordEncoder, addressRepository, categoryRepository, productRepository,
			variantRepository, imageRepository, jwtService, entityManager);
	}

	/* --- reads -------------------------------------------------------------- */

	@Test
	void listsOnlyTheCustomersOwnOrdersNewestFirst() throws Exception {
		Customer customer = fixture.newCustomer("read-own", 10);
		String token = fixture.tokenOf(customer);
		String first = fixture.placeOrder(token, customer, "read-key-one", 2);
		String second = fixture.placeOrder(token, customer, "read-key-two", 1);

		Customer stranger = fixture.newCustomer("read-other", 5);
		fixture.placeOrder(fixture.tokenOf(stranger), stranger, "read-key-other", 1);

		mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Success"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.data.length()").value(2))
			// newest first: the second order is at the top
			.andExpect(jsonPath("$.data[0].id").value(second))
			.andExpect(jsonPath("$.data[1].id").value(first))
			.andExpect(jsonPath("$.data[0].status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.data[0].paymentStatus").value("UNPAID"))
			.andExpect(jsonPath("$.data[0].grandTotalMinor")
				.value(OrderWorkflowFixture.BASE_PRICE + OrderWorkflowFixture.SHIPPING_FEE))
			.andExpect(jsonPath("$.data[0].itemCount").value(1))
			.andExpect(jsonPath("$.data[1].itemCount").value(2))
			.andExpect(jsonPath("$.data[0].orderNumber").isNotEmpty());
	}

	@Test
	void paginatesTheHistory() throws Exception {
		Customer customer = fixture.newCustomer("read-page", 10);
		String token = fixture.tokenOf(customer);
		fixture.placeOrder(token, customer, "page-key-one", 1);
		String second = fixture.placeOrder(token, customer, "page-key-two", 1);

		mockMvc.perform(get("/api/v1/orders?page=0&size=1")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.data[0].id").value(second));

		mockMvc.perform(get("/api/v1/orders?page=1&size=1")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void rejectsInvalidPagination() throws Exception {
		String token = fixture.registerCustomer("read-bad-page");

		mockMvc.perform(get("/api/v1/orders?page=-1")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.page").isNotEmpty());

		mockMvc.perform(get("/api/v1/orders?size=49")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.size").isNotEmpty());

		mockMvc.perform(get("/api/v1/orders?size=0")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void returnsTheOrderWithSnapshotsAndHistory() throws Exception {
		Customer customer = fixture.newCustomer("read-detail", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "detail-key", 2);

		mockMvc.perform(get("/api/v1/orders/" + orderId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(orderId))
			.andExpect(jsonPath("$.data.orderNumber").value(fixture.orderNumber(orderId)))
			.andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.data.shippingAddress.recipientName").value("An Nguyen"))
			.andExpect(jsonPath("$.data.shippingAddress.cityOrProvince")
				.value("Ho Chi Minh City"))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].quantity").value(2))
			.andExpect(jsonPath("$.data.items[0].productName").isNotEmpty())
			.andExpect(jsonPath("$.data.items[0].imageUrl").isNotEmpty())
			.andExpect(jsonPath("$.data.statusHistory.length()").value(1))
			.andExpect(jsonPath("$.data.statusHistory[0].fromStatus").doesNotExist())
			.andExpect(jsonPath("$.data.statusHistory[0].toStatus").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.data.statusHistory[0].note").value("Order placed"));
	}

	@Test
	void hidesAnotherCustomersOrderBehindANotFound() throws Exception {
		Customer owner = fixture.newCustomer("read-secret", 5);
		String orderId = fixture.placeOrder(fixture.tokenOf(owner), owner, "secret-key", 1);

		Customer intruder = fixture.newCustomer("read-intruder", 2);
		String intruderToken = fixture.tokenOf(intruder);

		for (String path : new String[] {"/api/v1/orders/" + orderId,
				"/api/v1/orders/" + orderId + "/cancel",
				"/api/v1/orders/" + orderId + "/return"}) {
			var request = path.endsWith("/cancel") || path.endsWith("/return")
				? post(path)
				: get(path);
			mockMvc.perform(request.header("Authorization", "Bearer " + intruderToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}

		// nothing changed for the real owner
		assertThat(fixture.statusOf(orderId)).isEqualTo("PENDING_PAYMENT");
		assertThat(fixture.stockOf(owner.variantId())).isEqualTo(4);
	}

	@Test
	void reportsAnUnknownOrderAsNotFound() throws Exception {
		String token = fixture.registerCustomer("read-missing");
		mockMvc.perform(get("/api/v1/orders/00000000-0000-0000-0000-000000000000")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void requiresAuthenticationForEveryOrderEndpoint() throws Exception {
		mockMvc.perform(get("/api/v1/orders"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/v1/orders/some-id"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/orders/some-id/cancel"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/orders"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aCustomerCannotUseTheAdminOrderEndpoints() throws Exception {
		Customer customer = fixture.newCustomer("read-not-admin", 2);
		String token = fixture.tokenOf(customer);
		fixture.placeOrder(token, customer, "not-admin-key", 1);

		mockMvc.perform(get("/api/v1/admin/orders").header("Authorization", "Bearer " + token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	/* --- cancel ------------------------------------------------------------- */

	@Test
	void cancelsAPendingOrderAndRestocksIt() throws Exception {
		Customer customer = fixture.newCustomer("cancel-pending", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "cancel-key", 3);
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(2);

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELLED"))
			.andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
			.andExpect(jsonPath("$.data.statusHistory.length()").value(2))
			.andExpect(jsonPath("$.data.statusHistory[1].fromStatus").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.data.statusHistory[1].toStatus").value("CANCELLED"));

		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(5);
		assertThat(fixture.statusOf(orderId)).isEqualTo("CANCELLED");
	}

	@Test
	void cancellingTwiceRestocksOnlyOnce() throws Exception {
		Customer customer = fixture.newCustomer("cancel-twice", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "cancel-twice-key", 2);

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(5);
		assertThat(fixture.historyCount(orderId)).isEqualTo(2);
	}

	@Test
	void aCustomerCannotCancelAProcessingOrder() throws Exception {
		Customer customer = fixture.newCustomer("cancel-processing", 5);
		Customer admin = fixture.newAdmin("cancel-admin");
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "cancel-proc-key", 2);

		payAndAdvance(orderId, token, fixture.adminToken(admin), "CONFIRMED", "PROCESSING");

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		assertThat(fixture.statusOf(orderId)).isEqualTo("PROCESSING");
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(3);
	}

	@Test
	void aCustomerCannotCancelAShippedOrderButCanAskForAReturn() throws Exception {
		Customer customer = fixture.newCustomer("return-shipped", 5);
		Customer admin = fixture.newAdmin("return-admin");
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "return-key", 2);
		payAndAdvance(orderId, token, fixture.adminToken(admin), "PROCESSING", "SHIPPED");

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/return")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURN_REQUESTED"));

		// no restock on request; the admin accepts the return later
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(3);
		mockMvc.perform(post("/api/v1/orders/" + orderId + "/return")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
	}

	/* --- helpers ------------------------------------------------------------ */

	/** Pays via the mock gateway, then applies the listed admin transitions. */
	void payAndAdvance(String orderId, String customerToken, String adminToken,
			String... statuses) throws Exception {
		mockMvc.perform(post("/api/v1/payments/" + orderId + "/mock/create")
				.header("Authorization", "Bearer " + customerToken))
			.andExpect(status().isOk());
		String paymentId = jdbcTemplate.queryForObject(
			"select id from payments where order_id = ? order by created_at limit 1",
			String.class, orderId);
		mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm")
				.header("Authorization", "Bearer " + customerToken))
			.andExpect(status().isOk());
		for (String status : statuses) {
			if ("CONFIRMED".equals(status) && "CONFIRMED".equals(fixture.statusOf(orderId))) {
				continue;
			}
			adminStatus(adminToken, orderId, status).andExpect(status().isOk());
		}
	}

	ResultActions adminStatus(String adminToken, String orderId, String status)
			throws Exception {
		return mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
			.header("Authorization", "Bearer " + adminToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"" + status + "\"}"));
	}
}
