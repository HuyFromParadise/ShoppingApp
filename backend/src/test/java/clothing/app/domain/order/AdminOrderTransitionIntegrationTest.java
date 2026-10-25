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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

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
 * Administrator transitions (DESIGN_REFERENCE §5.3, §7): the state machine and
 * its inventory side effects through the admin endpoint, including the
 * return workflow that is the only way to restock a delivered order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=1000",
	"app.orders.timeout-sweep-interval-ms=86400000"})
@Transactional
class AdminOrderTransitionIntegrationTest {

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

	@Test
	void walksTheHappyPathToDelivered() throws Exception {
		Customer admin = fixture.newAdmin("admin-happy");
		AdminContext context = paidOrder("admin-happy", admin, 2);

		change(context, "PROCESSING").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PROCESSING"));
		change(context, "SHIPPED").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("SHIPPED"));
		change(context, "DELIVERED").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("DELIVERED"))
			.andExpect(jsonPath("$.data.statusHistory.length()").value(5));

		// no stock movement after payment
		assertThat(fixture.stockOf(context.variantId())).isEqualTo(3);
	}

	@Test
	void rejectsTransitionsTheStateMachineForbids() throws Exception {
		Customer admin = fixture.newAdmin("admin-invalid");
		AdminContext context = paidOrder("admin-invalid", admin, 1);

		change(context, "SHIPPED").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
		change(context, "DELIVERED").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
		change(context, "LOST").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		change(context, "").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		assertThat(fixture.statusOf(context.orderId())).isEqualTo("CONFIRMED");
	}

	@Test
	void anAdminCancellationRestocksAndRefunds() throws Exception {
		Customer admin = fixture.newAdmin("admin-cancel");
		AdminContext context = paidOrder("admin-cancel", admin, 3);

		change(context, "CANCELLED").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELLED"))
			.andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

		assertThat(fixture.stockOf(context.variantId())).isEqualTo(5);
		assertThat(fixture.count("select count(*) from orders where status = 'CANCELLED' "
			+ "and id = ?", context.orderId())).isEqualTo(1);
	}

	@Test
	void anAcceptedReturnRestocksOnlyAtTheEnd() throws Exception {
		Customer admin = fixture.newAdmin("admin-return");
		AdminContext context = paidOrder("admin-return", admin, 2);
		change(context, "PROCESSING").andExpect(status().isOk());
		change(context, "SHIPPED").andExpect(status().isOk());
		change(context, "DELIVERED").andExpect(status().isOk());

		requestReturn(context).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURN_REQUESTED"));
		assertThat(fixture.stockOf(context.variantId())).isEqualTo(3);

		change(context, "RETURNED").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"));
		assertThat(fixture.stockOf(context.variantId())).isEqualTo(5);
	}

	@Test
	void aRejectedReturnDoesNotRestock() throws Exception {
		Customer admin = fixture.newAdmin("admin-reject-return");
		AdminContext context = paidOrder("admin-reject-return", admin, 2);
		change(context, "PROCESSING").andExpect(status().isOk());
		change(context, "SHIPPED").andExpect(status().isOk());
		requestReturn(context).andExpect(status().isOk());

		change(context, "SHIPPED").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("SHIPPED"));
		assertThat(fixture.stockOf(context.variantId())).isEqualTo(3);
	}

	@Test
	void recordsWhoChangedTheStatusAndWhy() throws Exception {
		Customer admin = fixture.newAdmin("admin-note");
		AdminContext context = paidOrder("admin-note", admin, 1);

		change(context, "PROCESSING", "Packed at warehouse")
			.andExpect(status().isOk());

		assertThat(jdbcTemplate.queryForObject(
			"select note from order_status_history where order_id = ? order by created_at desc limit 1",
			String.class, context.orderId())).isEqualTo("Packed at warehouse");
	}

	@Test
	void showsAnyOrderToAnAdmin() throws Exception {
		Customer admin = fixture.newAdmin("admin-detail");
		AdminContext context = paidOrder("admin-detail", admin, 1);

		mockMvc.perform(get("/api/v1/admin/orders/" + context.orderId())
				.header("Authorization", "Bearer " + context.adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(context.orderId()))
			.andExpect(jsonPath("$.data.status").value("CONFIRMED"));

		mockMvc.perform(get("/api/v1/admin/orders").header("Authorization",
				"Bearer " + context.adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray());

		mockMvc.perform(get("/api/v1/admin/orders/does-not-exist")
				.header("Authorization", "Bearer " + context.adminToken()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	/* --- helpers ------------------------------------------------------------ */

	record AdminContext(String orderId, String customerToken, String adminToken,
			String variantId, String customerId) {
	}

	/** An order paid through the mock gateway, which leaves it CONFIRMED. */
	private AdminContext paidOrder(String slugHint, Customer admin, int quantity)
			throws Exception {
		Customer customer = fixture.newCustomer(slugHint + "-customer", 5);
		String customerToken = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(customerToken, customer,
			slugHint + "-idem-key", quantity);
		MvcResult created = mockMvc.perform(post("/api/v1/payments/" + orderId + "/mock/create")
				.header("Authorization", "Bearer " + customerToken))
			.andExpect(status().isOk())
			.andReturn();
		String paymentId = JsonPath.read(created.getResponse().getContentAsString(),
			"$.data.paymentId");
		mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm")
				.header("Authorization", "Bearer " + customerToken))
			.andExpect(status().isOk());
		return new AdminContext(orderId, customerToken, fixture.adminToken(admin),
			customer.variantId(), customer.id());
	}

	private org.springframework.test.web.servlet.ResultActions change(AdminContext context,
			String status) throws Exception {
		return change(context, status, null);
	}

	private org.springframework.test.web.servlet.ResultActions change(AdminContext context,
			String status, String note) throws Exception {
		String body = note == null
			? "{\"status\":\"" + status + "\"}"
			: "{\"status\":\"" + status + "\",\"note\":\"" + note + "\"}";
		return mockMvc.perform(patch("/api/v1/admin/orders/" + context.orderId() + "/status")
			.header("Authorization", "Bearer " + context.adminToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private org.springframework.test.web.servlet.ResultActions requestReturn(AdminContext context)
			throws Exception {
		// the return request is the customer's, not the admin's
		return mockMvc.perform(post("/api/v1/orders/" + context.orderId() + "/return")
			.header("Authorization", "Bearer " + context.customerToken()));
	}
}
