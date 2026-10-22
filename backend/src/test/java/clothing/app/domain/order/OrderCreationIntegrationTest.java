package clothing.app.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.domain.address.AddressEntity;
import clothing.app.domain.address.AddressRepository;
import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;

/**
 * Order creation contract (DESIGN_REFERENCE §8) against the real MySQL schema:
 * atomic stock decrement, server totals, address/product snapshots, the initial
 * status and payment rows, cart clearing, idempotency, and rollback on failure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "RATE_LIMIT_AUTH_CAPACITY=1000")
@Transactional
class OrderCreationIntegrationTest {

	private static final long BASE_PRICE = 499000L;
	private static final long STANDARD_FEE = 30000L;
	private static final String KEY = "order-key-0001";
	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductVariantRepository variantRepository;

	@Autowired
	private ProductImageRepository imageRepository;

	@Autowired
	private AddressRepository addressRepository;

	private ProductEntity shirt;
	private ProductVariantEntity stocked;
	private ProductVariantEntity discounted;

	@BeforeEach
	void seedCatalog() {
		CategoryEntity category = categoryRepository.save(category("Order Tops", "order-tops"));
		shirt = productRepository.save(product(category, "Order Shirt", "order-shirt",
			ProductEntity.Status.ACTIVE));
		stocked = variantRepository.save(variant(shirt, "ORD-M-WHITE", "M", "White", null, 5));
		discounted = variantRepository.save(
			variant(shirt, "ORD-L-WHITE", "L", "White", 399000L, 3));

		ProductImageEntity image = new ProductImageEntity();
		image.setProductId(shirt.getId());
		image.setStorageKey("/images/order-shirt.jpg");
		image.setAltText("Order Shirt");
		image.setSortOrder(0);
		image.setIsPrimary(Boolean.TRUE);
		imageRepository.save(image);
	}

	/* --- success ----------------------------------------------------------- */

	@Test
	void createsTheOrderWithSnapshotsAndDeductsStock() throws Exception {
		String token = register("order-ok@example.com");
		String userId = userIdOf(token);
		String addressId = addressOf(userId);
		addItem(token, stocked, 2);
		addItem(token, discounted, 1);

		MvcResult result = create(token, KEY, body(addressId, "STANDARD",
			"Leave at reception"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.message").value("Success"))
			.andExpect(jsonPath("$.data.id").isNotEmpty())
			.andExpect(jsonPath("$.data.orderNumber", matchesPattern("ORD-[0-9A-Z]{8}")))
			.andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
			.andExpect(jsonPath("$.data.currency").value("VND"))
			.andExpect(jsonPath("$.data.subtotalMinor").value(BASE_PRICE * 2 + 399000L))
			.andExpect(jsonPath("$.data.discountTotalMinor").value(0))
			.andExpect(jsonPath("$.data.shippingTotalMinor").value(STANDARD_FEE))
			.andExpect(jsonPath("$.data.grandTotalMinor")
				.value(BASE_PRICE * 2 + 399000L + STANDARD_FEE))
			.andExpect(jsonPath("$.data.shippingMethodCode").value("STANDARD"))
			.andExpect(jsonPath("$.data.shippingMethodName").value("Standard delivery"))
			.andExpect(jsonPath("$.data.customerNote").value("Leave at reception"))
			.andExpect(jsonPath("$.data.placedAt").isNotEmpty())
			.andExpect(jsonPath("$.data.shippingAddress.recipientName").value("An Nguyen"))
			.andExpect(jsonPath("$.data.shippingAddress.phone").value("0901234567"))
			.andExpect(jsonPath("$.data.shippingAddress.addressLine1").value("12 Le Loi"))
			.andExpect(jsonPath("$.data.shippingAddress.ward").value("Ben Thanh"))
			.andExpect(jsonPath("$.data.shippingAddress.district").value("District 1"))
			.andExpect(jsonPath("$.data.shippingAddress.cityOrProvince")
				.value("Ho Chi Minh City"))
			.andExpect(jsonPath("$.data.shippingAddress.postalCode").value("700000"))
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andReturn();

		flush();
		String orderId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

		// snapshots survive later catalog edits
		Map<String, Object> shirtLine = jdbcTemplate.queryForMap(
			"select product_name_snapshot, size_snapshot, color_snapshot, "
				+ "image_url_snapshot, unit_price, quantity, line_total from order_items "
				+ "where order_id = ? and sku_snapshot = 'ORD-M-WHITE'", orderId);
		assertThat(shirtLine.get("product_name_snapshot")).isEqualTo("Order Shirt");
		assertThat(shirtLine.get("size_snapshot")).isEqualTo("M");
		assertThat(shirtLine.get("color_snapshot")).isEqualTo("White");
		assertThat(shirtLine.get("image_url_snapshot")).isEqualTo("/images/order-shirt.jpg");
		assertThat(((Number) shirtLine.get("unit_price")).longValue()).isEqualTo(BASE_PRICE);
		assertThat(((Number) shirtLine.get("quantity")).intValue()).isEqualTo(2);
		assertThat(((Number) shirtLine.get("line_total")).longValue())
			.isEqualTo(BASE_PRICE * 2);

		Map<String, Object> discountedLine = jdbcTemplate.queryForMap(
			"select unit_price, quantity from order_items where order_id = ? "
				+ "and sku_snapshot = 'ORD-L-WHITE'", orderId);
		assertThat(((Number) discountedLine.get("unit_price")).longValue()).isEqualTo(399000L);
		assertThat(((Number) discountedLine.get("quantity")).intValue()).isEqualTo(1);

		assertThat(stockOf(stocked)).isEqualTo(3);
		assertThat(stockOf(discounted)).isEqualTo(2);

		assertThat(count("select count(*) from order_status_history where order_id = ?",
			orderId)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"select to_status from order_status_history where order_id = ?", String.class,
			orderId)).isEqualTo("PENDING_PAYMENT");

		Map<String, Object> payment = jdbcTemplate.queryForMap(
			"select provider, status, amount, currency from payments where order_id = ?",
			orderId);
		assertThat(payment.get("provider")).isEqualTo("MOCK");
		assertThat(payment.get("status")).isEqualTo("PENDING");
		assertThat(((Number) payment.get("amount")).longValue())
			.isEqualTo(BASE_PRICE * 2 + 399000L + STANDARD_FEE);
		assertThat(payment.get("currency")).isEqualTo("VND");

		assertThat(count("select count(*) from idempotency_records where user_id = ?", userId))
			.isEqualTo(1);

		// the cart is spent
		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items.length()").value(0))
			.andExpect(jsonPath("$.data.subtotalMinor").value(0));
	}

	@Test
	void ignoresAnyClientSuppliedAmount() throws Exception {
		String token = register("order-amounts@example.com");
		String addressId = addressOf(userIdOf(token));
		addItem(token, stocked, 1);

		mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.header(IDEMPOTENCY_HEADER, "order-key-amount")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":\"" + addressId + "\","
					+ "\"shippingMethod\":\"STANDARD\","
					+ "\"subtotalMinor\":1,\"shippingTotalMinor\":0,\"grandTotalMinor\":1}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.subtotalMinor").value(BASE_PRICE))
			.andExpect(jsonPath("$.data.shippingTotalMinor").value(STANDARD_FEE))
			.andExpect(jsonPath("$.data.grandTotalMinor").value(BASE_PRICE + STANDARD_FEE));
	}

	/* --- idempotency ------------------------------------------------------- */

	@Test
	void replaysACompletedKeyInsteadOfChargingTwice() throws Exception {
		String token = register("order-replay@example.com");
		String userId = userIdOf(token);
		String addressId = addressOf(userId);
		addItem(token, stocked, 2);

		MvcResult first = create(token, KEY, body(addressId, "STANDARD", null))
			.andExpect(status().isCreated())
			.andReturn();
		String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.id");
		String firstNumber = JsonPath.read(first.getResponse().getContentAsString(),
			"$.data.orderNumber");

		// the cart is already empty; the replay must not re-price it
		MvcResult replay = create(token, KEY, body(addressId, "STANDARD", null))
			.andExpect(status().isCreated())
			.andReturn();
		String replayBody = replay.getResponse().getContentAsString();
		assertThat((String) JsonPath.read(replayBody, "$.data.id")).isEqualTo(firstId);
		assertThat((String) JsonPath.read(replayBody, "$.data.orderNumber"))
			.isEqualTo(firstNumber);
		assertThat((String) JsonPath.read(replayBody, "$.data.placedAt")).isNotEmpty();

		flush();
		assertThat(count("select count(*) from orders where user_id = ?", userId)).isEqualTo(1);
		assertThat(count("select count(*) from idempotency_records where user_id = ?", userId))
			.isEqualTo(1);
		// stock moved once, not twice
		assertThat(stockOf(stocked)).isEqualTo(3);
	}

	@Test
	void rejectsAKeyReusedWithADifferentRequest() throws Exception {
		String token = register("order-reuse@example.com");
		String userId = userIdOf(token);
		String addressId = addressOf(userId);
		addItem(token, stocked, 1);

		create(token, KEY, body(addressId, "STANDARD", "First note"))
			.andExpect(status().isCreated());

		create(token, KEY, body(addressId, "STANDARD", "Different note"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

		flush();
		assertThat(count("select count(*) from orders where user_id = ?", userId)).isEqualTo(1);
	}

	@Test
	void requiresAWellFormedIdempotencyKey() throws Exception {
		String token = register("order-key@example.com");
		String addressId = addressOf(userIdOf(token));
		addItem(token, stocked, 1);

		mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(addressId, "STANDARD", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors['Idempotency-Key']").isNotEmpty());

		create(token, "short", body(addressId, "STANDARD", null))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	/* --- failures and rollback --------------------------------------------- */

	@Test
	void rollsBackWhenTheStockNoLongerCoversTheCart() throws Exception {
		String token = register("order-stock@example.com");
		String userId = userIdOf(token);
		String addressId = addressOf(userId);
		addItem(token, stocked, 5);

		stocked.setStockQuantity(2);
		variantRepository.save(stocked);

		create(token, KEY, body(addressId, "STANDARD", null))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
			.andExpect(jsonPath("$.message").value("Only 2 left in stock"));

		flush();
		assertThat(stockOf(stocked)).isEqualTo(2);
		assertThat(count("select count(*) from orders where user_id = ?", userId)).isEqualTo(0);
		assertThat(count("select count(*) from idempotency_records where user_id = ?", userId))
			.isEqualTo(0);
		// the cart is untouched, so the customer can fix the quantity and retry
		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].quantity").value(5));
	}

	@Test
	void rollsBackWhenAProductIsNoLongerAvailable() throws Exception {
		String token = register("order-archived@example.com");
		String userId = userIdOf(token);
		String addressId = addressOf(userId);
		addItem(token, stocked, 1);

		shirt.setStatus(ProductEntity.Status.ARCHIVED);
		productRepository.save(shirt);

		create(token, KEY, body(addressId, "STANDARD", null))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.message").value("Order Shirt is no longer available"));

		flush();
		assertThat(stockOf(stocked)).isEqualTo(5);
		assertThat(count("select count(*) from orders where user_id = ?", userId)).isEqualTo(0);
	}

	@Test
	void neverUsesAnotherCustomersAddress() throws Exception {
		String other = register("order-other@example.com");
		String otherAddress = addressOf(userIdOf(other));

		String token = register("order-mine@example.com");
		String userId = userIdOf(token);
		addItem(token, stocked, 1);

		create(token, KEY, body(otherAddress, "STANDARD", null))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		flush();
		assertThat(count("select count(*) from orders where user_id = ?", userId)).isEqualTo(0);
		assertThat(stockOf(stocked)).isEqualTo(5);
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header(IDEMPOTENCY_HEADER, KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":\"a1\",\"shippingMethod\":\"STANDARD\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	/* --- helpers ----------------------------------------------------------- */

	private org.springframework.test.web.servlet.ResultActions create(String token, String key,
			String body) throws Exception {
		return mockMvc.perform(post("/api/v1/orders")
			.header("Authorization", "Bearer " + token)
			.header(IDEMPOTENCY_HEADER, key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static String body(String addressId, String shippingMethod, String note) {
		StringBuilder json = new StringBuilder("{\"addressId\":\"").append(addressId)
			.append("\",\"shippingMethod\":\"").append(shippingMethod).append('"');
		if (note != null) {
			json.append(",\"customerNote\":\"").append(note).append('"');
		}
		return json.append('}').toString();
	}

	private void flush() {
		entityManager.flush();
	}

	private int stockOf(ProductVariantEntity variant) {
		Integer stock = jdbcTemplate.queryForObject(
			"select stock_quantity from product_variants where id = ?", Integer.class,
			variant.getId());
		return stock == null ? -1 : stock;
	}

	private int count(String sql, Object... args) {
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
		return count == null ? -1 : count;
	}

	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass!234\","
					+ "\"firstName\":\"An\",\"lastName\":\"Nguyen\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
	}

	private String userIdOf(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private String addressOf(String userId) {
		AddressEntity address = new AddressEntity();
		address.setUserId(userId);
		address.setRecipientName("An Nguyen");
		address.setPhone("0901234567");
		address.setAddressLine1("12 Le Loi");
		address.setAddressLine2("Floor 3");
		address.setWard("Ben Thanh");
		address.setDistrict("District 1");
		address.setCityOrProvince("Ho Chi Minh City");
		address.setPostalCode("700000");
		address.setIsDefault(Boolean.TRUE);
		return addressRepository.save(address).getId();
	}

	private void addItem(String token, ProductVariantEntity variant, int quantity)
			throws Exception {
		mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + variant.getId() + "\",\"quantity\":"
					+ quantity + "}"))
			.andExpect(status().isOk());
	}

	/* --- fixtures ---------------------------------------------------------- */

	private static CategoryEntity category(String name, String slug) {
		CategoryEntity category = new CategoryEntity();
		category.setName(name);
		category.setSlug(slug);
		category.setDescription(name);
		category.setIsActive(Boolean.TRUE);
		category.setSortOrder(1);
		return category;
	}

	private static ProductEntity product(CategoryEntity category, String name, String slug,
			ProductEntity.Status status) {
		ProductEntity product = new ProductEntity();
		product.setName(name);
		product.setSlug(slug);
		product.setShortDescription(name);
		product.setDescription(name);
		product.setCategoryId(category.getId());
		product.setBasePrice(BASE_PRICE);
		product.setCurrency("VND");
		product.setStatus(status);
		product.setFeatured(Boolean.FALSE);
		return product;
	}

	private static ProductVariantEntity variant(ProductEntity product, String sku, String size,
			String color, Long priceOverride, int stock) {
		ProductVariantEntity variant = new ProductVariantEntity();
		variant.setProductId(product.getId());
		variant.setSku(sku);
		variant.setSize(size);
		variant.setColor(color);
		variant.setPriceOverride(priceOverride);
		variant.setStockQuantity(stock);
		variant.setIsActive(Boolean.TRUE);
		return variant;
	}
}
