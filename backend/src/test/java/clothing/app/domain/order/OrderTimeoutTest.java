package clothing.app.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

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
import clothing.app.domain.payment.PaymentService;
import clothing.app.domain.user.UserRepository;

/**
 * The ADR-005 timeout sweep: an unpaid order past the payment window is
 * cancelled and restocked, a paid one is left alone, and a late payment
 * callback cannot resurrect a cancelled order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=1000",
	"app.orders.payment-window-minutes=30",
	// far in the future so the scheduled runner never interferes with these tests
	"app.orders.timeout-sweep-interval-ms=86400000",
	"VNPAY_TMN_CODE=TESTTMN", "VNPAY_HASH_SECRET=integration-test-only-secret",
	"VNPAY_PAY_URL=https://sandbox.invalid/pay"})
@Transactional
class OrderTimeoutTest {

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

	@Autowired
	private OrderTimeoutService timeoutService;

	@Autowired
	private PaymentService paymentService;

	private OrderWorkflowFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new OrderWorkflowFixture(mockMvc, jdbcTemplate, userRepository,
			passwordEncoder, addressRepository, categoryRepository, productRepository,
			variantRepository, imageRepository, jwtService, entityManager);
	}

	@Test
	void cancelsAndRestocksAnUnpaidOrderPastTheWindow() throws Exception {
		Customer customer = fixture.newCustomer("timeout-expired", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "timeout-key", 2);
		backdate(orderId, Instant.now().minusSeconds(31 * 60));

		int cancelled = timeoutService.cancelExpired(Instant.now());

		assertThat(cancelled).isGreaterThanOrEqualTo(1);
		assertThat(fixture.statusOf(orderId)).isEqualTo("CANCELLED");
		assertThat(fixture.paymentStatusOf(orderId)).isEqualTo("UNPAID");
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(5);
		assertThat(jdbcTemplate.queryForObject(
			"select note from order_status_history where order_id = ? order by created_at desc limit 1",
			String.class, orderId)).isEqualTo("Payment window expired");

		// a second sweep must not release the stock again
		timeoutService.cancelExpired(Instant.now());
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(5);
		assertThat(fixture.historyCount(orderId)).isEqualTo(2);
	}

	@Test
	void leavesAnOrderInsideTheWindowAlone() throws Exception {
		Customer customer = fixture.newCustomer("timeout-fresh", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "timeout-fresh-key", 2);
		backdate(orderId, Instant.now().minusSeconds(10 * 60));

		timeoutService.cancelExpired(Instant.now());

		assertThat(fixture.statusOf(orderId)).isEqualTo("PENDING_PAYMENT");
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(3);
	}

	@Test
	void leavesAPaidOrderAloneHoweverOld() throws Exception {
		Customer customer = fixture.newCustomer("timeout-paid", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "timeout-paid-key", 2);

		paymentService.createMock(customer.id(), orderId, "127.0.0.1");
		String paymentId = jdbcTemplate.queryForObject(
			"select id from payments where order_id = ? order by created_at limit 1",
			String.class, orderId);
		paymentService.confirmMock(customer.id(), paymentId, true);
		backdate(orderId, Instant.now().minusSeconds(120 * 60));

		timeoutService.cancelExpired(Instant.now());

		assertThat(fixture.statusOf(orderId)).isEqualTo("CONFIRMED");
		assertThat(fixture.paymentStatusOf(orderId)).isEqualTo("PAID");
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(3);
	}

	@Test
	void aLatePaymentCallbackCannotResurrectACancelledOrder() throws Exception {
		Customer customer = fixture.newCustomer("timeout-late", 5);
		String token = fixture.tokenOf(customer);
		String orderId = fixture.placeOrder(token, customer, "timeout-late-key", 2);
		backdate(orderId, Instant.now().minusSeconds(45 * 60));
		timeoutService.cancelExpired(Instant.now());
		assertThat(fixture.statusOf(orderId)).isEqualTo("CANCELLED");

		Map<String, String> params = Map.of(
			"vnp_TxnRef", fixture.orderNumber(orderId),
			"vnp_TransactionNo", "14999999",
			"vnp_ResponseCode", "00",
			"vnp_TransactionStatus", "00");
		var callback = new java.util.HashMap<>(params);
		callback.put("vnp_SecureHash", clothing.app.domain.payment.VnPayGateway.hmacSha512(
			clothing.app.domain.payment.VnPayTestSigner.SECRET,
			clothing.app.domain.payment.VnPayGateway.canonicalQuery(params)));

		paymentService.handleVnPayCallback(callback);

		// the callback is recorded, but the order stays cancelled and restocked
		assertThat(fixture.statusOf(orderId)).isEqualTo("CANCELLED");
		assertThat(fixture.stockOf(customer.variantId())).isEqualTo(5);
		assertThat(fixture.count("select count(*) from payment_events where provider = 'VNPAY'"))
			.isEqualTo(1);
	}

	private void backdate(String orderId, Instant placedAt) {
		entityManager.flush();
		jdbcTemplate.update("update orders set placed_at = ? where id = ?",
			java.sql.Timestamp.from(placedAt), orderId);
		// Local copies of the row are stale now; reload them from the database.
		entityManager.clear();
	}
}
