package clothing.app.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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
 * Payment workflow (DESIGN_REFERENCE §5.3, §7; ADR-007): the mock gateway, the
 * unconfigured VNPay skeleton, and the VNPay webhook with signature checks and
 * {@code payment_events} deduplication.
 *
 * <p>The webhook tests run with VNPAY_* supplied through test properties. They
 * are throwaway values generated in this file — they are not real credentials
 * and configure nothing outside the test JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=1000",
	"app.orders.timeout-sweep-interval-ms=3600000",
	"VNPAY_TMN_CODE=TESTTMN", "VNPAY_HASH_SECRET=integration-test-only-secret",
	"VNPAY_PAY_URL=https://sandbox.invalid/pay",
	"app.payment.vnpay.return-url=http://localhost:3000/account/orders"})
@Transactional
class PaymentWorkflowIntegrationTest {

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

	/* --- mock gateway ------------------------------------------------------- */

	@Test
	void theMockGatewayIsLabelledAndSettlesTheOrderExactlyOnce() throws Exception {
		Customer customer = fixture.newCustomer("pay-mock", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "pay-mock-key", 2);

		MvcResult created = mockMvc.perform(post("/api/v1/payments/" + orderId + "/mock/create")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.provider").value("MOCK"))
			.andExpect(jsonPath("$.data.mock").value(true))
			.andExpect(jsonPath("$.data.status").value("PENDING"))
			.andExpect(jsonPath("$.data.paymentUrl", containsString("/payments/mock/")))
			.andReturn();
		String paymentId = JsonPath.read(created.getResponse().getContentAsString(),
			"$.data.paymentId");
		assertThat(fixture.paymentStatusOf(orderId)).isEqualTo("PENDING");

		mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paid").value(true))
			.andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"))
			.andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

		assertThat(fixture.statusOf(orderId)).isEqualTo("CONFIRMED");
		assertThat(fixture.paymentStatusOf(orderId)).isEqualTo("PAID");
		assertThat(jdbcTemplate.queryForObject(
			"select status from payments where id = ?", String.class, paymentId))
			.isEqualTo("PAID");
		// the payment success does not move stock a second time
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(3);

		// a refresh of the confirmation page must not transition the order again
		mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));
		assertThat(fixture.historyCount(orderId)).isEqualTo(2);
	}

	@Test
	void aDeclinedMockPaymentMarksFailureButKeepsTheOrderPayable() throws Exception {
		Customer customer = fixture.newCustomer("pay-decline", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "pay-decline-key", 1);
		String paymentId = startMock(token, orderId);

		mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm?approve=false")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paid").value(false))
			.andExpect(jsonPath("$.data.orderStatus").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));

		assertThat(fixture.statusOf(orderId)).isEqualTo("PENDING_PAYMENT");
		assertThat(jdbcTemplate.queryForObject(
			"select status from payments where id = ?", String.class, paymentId))
			.isEqualTo("FAILED");

		// the customer can retry: a fresh attempt still works
		String retry = startMock(token, orderId);
		mockMvc.perform(post("/api/v1/payments/mock/" + retry + "/confirm")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));
	}

	@Test
	void anotherCustomerCannotStartOrConfirmAPayment() throws Exception {
		Customer owner = fixture.newCustomer("pay-owner", 5);
		String ownerToken = fixture.tokenOf(owner);
		String orderId = fixture.placeOrder(ownerToken, owner, "pay-owner-key", 1);
		String paymentId = startMock(ownerToken, orderId);

		Customer intruder = fixture.newCustomer("pay-intruder", 1);
		String intruderToken = fixture.tokenOf(intruder);

		mockMvc.perform(post("/api/v1/payments/" + orderId + "/mock/create")
				.header("Authorization", "Bearer " + intruderToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm")
				.header("Authorization", "Bearer " + intruderToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		assertThat(fixture.paymentStatusOf(orderId)).isEqualTo("PENDING");
	}

	@Test
	void aCancelledOrderCannotBePaid() throws Exception {
		Customer customer = fixture.newCustomer("pay-cancelled", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "pay-cancelled-key", 1);
		String paymentId = startMock(token, orderId);

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/payments/" + orderId + "/mock/create")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

		// the confirmation page of the earlier attempt cannot resurrect it
		mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"))
			.andExpect(jsonPath("$.data.paid").value(true));
		assertThat(fixture.statusOf(orderId)).isEqualTo("CANCELLED");
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(5);
	}

	@Test
	void listsThePaymentAttemptsOfAnOwnedOrder() throws Exception {
		Customer customer = fixture.newCustomer("pay-list", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "pay-list-key", 1);

		mockMvc.perform(get("/api/v1/payments/" + orderId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].provider").value("MOCK"))
			.andExpect(jsonPath("$.data[0].status").value("PENDING"));
	}

	@Test
	void aCancelledPaidOrderIsRefundedAndRestocked() throws Exception {
		Customer customer = fixture.newCustomer("pay-refund", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "pay-refund-key", 2);
		String paymentId = startMock(token, orderId);
		mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELLED"))
			.andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(5);
	}

	/* --- VNPay ------------------------------------------------------------- */

	@Test
	void vnPayCreatesAProviderUrlAndMarksTheOrderPending() throws Exception {
		Customer customer = fixture.newCustomer("pay-vnpay", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "pay-vnpay-key", 1);

		mockMvc.perform(post("/api/v1/payments/" + orderId + "/vnpay/create")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.provider").value("VNPAY"))
			.andExpect(jsonPath("$.data.mock").value(false))
			.andExpect(jsonPath("$.data.paymentUrl",
				startsWith("https://sandbox.invalid/pay?")))
			.andExpect(jsonPath("$.data.paymentUrl",
				containsString("vnp_TxnRef=" + fixture.orderNumber(orderId))));

		assertThat(fixture.paymentStatusOf(orderId)).isEqualTo("PENDING");
	}

	@Test
	void anUnsignedWebhookIsForbiddenEvenWhenVNPayIsConfigured() throws Exception {
		Customer customer = fixture.newCustomer("hook-unsigned", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "hook-unsigned-key", 1);

		mockMvc.perform(post("/api/v1/payments/webhooks/vnpay")
				.contentType("application/x-www-form-urlencoded")
				.param("vnp_TxnRef", fixture.orderNumber(orderId))
				.param("vnp_ResponseCode", "00"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		mockMvc.perform(post("/api/v1/payments/webhooks/vnpay")
				.contentType("application/x-www-form-urlencoded")
				.param("vnp_TxnRef", fixture.orderNumber(orderId))
				.param("vnp_ResponseCode", "00")
				.param("vnp_SecureHash", "deadbeef"))
			.andExpect(status().isForbidden());

		assertThat(fixture.statusOf(orderId)).isEqualTo("PENDING_PAYMENT");
	}

	@Test
	void aSignedWebhookConfirmsTheOrderAndReplaysAreIgnored() throws Exception {
		Customer customer = fixture.newCustomer("hook-paid", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "hook-paid-key", 2);
		String txnRef = fixture.orderNumber(orderId);

		Map<String, String> signed = VnPayTestSigner.sign(Map.of(
			"vnp_TxnRef", txnRef,
			"vnp_TransactionNo", "14123456",
			"vnp_ResponseCode", "00",
			"vnp_TransactionStatus", "00",
			"vnp_Amount", Long.toString(OrderWorkflowFixture.BASE_PRICE
				+ OrderWorkflowFixture.SHIPPING_FEE)));

		mockMvc.perform(post("/api/v1/payments/webhooks/vnpay")
				.contentType("application/x-www-form-urlencoded")
				.params(VnPayTestSigner.params(signed)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paid").value(true))
			.andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));

		assertThat(fixture.statusOf(orderId)).isEqualTo("CONFIRMED");
		assertThat(fixture.paymentStatusOf(orderId)).isEqualTo("PAID");
		assertThat(fixture.count(
			"select count(*) from payment_events where provider = 'VNPAY'")).isEqualTo(1);
		assertThat(fixture.historyCount(orderId)).isEqualTo(2);

		// the provider retries the same event: no second transition or release
		mockMvc.perform(post("/api/v1/payments/webhooks/vnpay")
				.contentType("application/x-www-form-urlencoded")
				.params(VnPayTestSigner.params(signed)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paid").value(true));

		assertThat(fixture.count(
			"select count(*) from payment_events where provider = 'VNPAY'")).isEqualTo(1);
		assertThat(fixture.historyCount(orderId)).isEqualTo(2);
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(3);
	}

	@Test
	void aSignedFailureWebhookRecordsAFailedAttempt() throws Exception {
		Customer customer = fixture.newCustomer("hook-failed", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "hook-failed-key", 1);

		Map<String, String> signed = VnPayTestSigner.sign(Map.of(
			"vnp_TxnRef", fixture.orderNumber(orderId),
			"vnp_TransactionNo", "14123457",
			"vnp_ResponseCode", "24",
			"vnp_TransactionStatus", "02"));

		mockMvc.perform(post("/api/v1/payments/webhooks/vnpay")
				.contentType("application/x-www-form-urlencoded")
				.params(VnPayTestSigner.params(signed)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.paid").value(false));

		assertThat(fixture.statusOf(orderId)).isEqualTo("PENDING_PAYMENT");
		assertThat(fixture.paymentStatusOf(orderId)).isEqualTo("FAILED");
	}

	@Test
	void aTamperedSignedPayloadIsRejected() throws Exception {
		Customer customer = fixture.newCustomer("hook-tampered", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "hook-tampered-key", 1);

		Map<String, String> signed = VnPayTestSigner.sign(Map.of(
			"vnp_TxnRef", fixture.orderNumber(orderId),
			"vnp_ResponseCode", "00"));
		signed.put("vnp_ResponseCode", "24");

		mockMvc.perform(post("/api/v1/payments/webhooks/vnpay")
				.contentType("application/x-www-form-urlencoded")
				.params(VnPayTestSigner.params(signed)))
			.andExpect(status().isForbidden());

		assertThat(fixture.paymentStatusOf(orderId)).isEqualTo("UNPAID");
		assertThat(fixture.statusOf(orderId)).isEqualTo("PENDING_PAYMENT");
		assertThat(fixture.count("select count(*) from payment_events where provider = 'VNPAY'"))
			.isZero();
	}

	private String startMock(String token, String orderId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/payments/" + orderId + "/mock/create")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.paymentId");
	}
}
