package clothing.app.domain.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

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
 * Quote contract against the real MySQL schema (DESIGN_REFERENCE §5.3, §8):
 * server-computed totals, address ownership, catalog re-validation, stock, and
 * the P1 promotion boundary. A quote never writes or holds stock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "RATE_LIMIT_AUTH_CAPACITY=1000")
@Transactional
class CheckoutQuoteIntegrationTest {

	private static final long BASE_PRICE = 499000L;
	private static final long STANDARD_FEE = 30000L;

	@Autowired
	private MockMvc mockMvc;

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

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private ProductEntity shirt;
	private ProductVariantEntity stocked;
	private ProductVariantEntity discounted;
	private ProductVariantEntity scarce;

	@BeforeEach
	void seedCatalog() {
		CategoryEntity category = categoryRepository.save(category("Checkout Tops", "checkout-tops"));
		shirt = productRepository.save(product(category, "Checkout Shirt", "checkout-shirt",
			ProductEntity.Status.ACTIVE));
		stocked = variantRepository.save(variant(shirt, "CHK-M-WHITE", "M", "White", null, 5));
		discounted = variantRepository.save(
			variant(shirt, "CHK-L-WHITE", "L", "White", 399000L, 3));
		scarce = variantRepository.save(variant(shirt, "CHK-S-WHITE", "S", "White", null, 2));

		ProductImageEntity image = new ProductImageEntity();
		image.setProductId(shirt.getId());
		image.setStorageKey("/images/checkout-shirt.jpg");
		image.setAltText("Checkout Shirt");
		image.setSortOrder(0);
		image.setIsPrimary(Boolean.TRUE);
		imageRepository.save(image);
	}

	/* --- tests ------------------------------------------------------------- */

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/checkout/quote")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":\"a1\",\"shippingMethod\":\"STANDARD\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void quotesTheCartFromTheCatalogAndTheConfiguredFee() throws Exception {
		String token = register("quote-ok@example.com");
		String addressId = addressOf(userIdOf(token));
		addItem(token, stocked, 2);
		addItem(token, discounted, 1);

		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(addressId, "STANDARD", null)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.currency").value("VND"))
			.andExpect(jsonPath("$.data.itemCount").value(3))
			// 2 x 499.000 + 1 x 399.000 override
			.andExpect(jsonPath("$.data.subtotalMinor").value(BASE_PRICE * 2 + 399000L))
			.andExpect(jsonPath("$.data.discountTotalMinor").value(0))
			.andExpect(jsonPath("$.data.shippingTotalMinor").value(STANDARD_FEE))
			.andExpect(jsonPath("$.data.grandTotalMinor")
				.value(BASE_PRICE * 2 + 399000L + STANDARD_FEE))
			.andExpect(jsonPath("$.data.shippingMethodCode").value("STANDARD"))
			.andExpect(jsonPath("$.data.shippingMethodName").value("Standard delivery"));

		// A quote is a read: it neither decrements nor reserves stock.
		assertThat(stockOf(stocked)).isEqualTo(5);
	}

	@Test
	void acceptsTheShippingCodeInAnyCaseAndChargesItsOwnFee() throws Exception {
		String token = register("quote-express@example.com");
		String addressId = addressOf(userIdOf(token));
		addItem(token, stocked, 1);

		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(addressId, "express", null)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.shippingMethodCode").value("EXPRESS"))
			.andExpect(jsonPath("$.data.shippingTotalMinor").value(60000))
			.andExpect(jsonPath("$.data.grandTotalMinor").value(BASE_PRICE + 60000L));
	}

	@Test
	void rejectsAnUnknownShippingMethod() throws Exception {
		String token = register("quote-shipping@example.com");
		String addressId = addressOf(userIdOf(token));
		addItem(token, stocked, 1);

		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(addressId, "DRONE", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.shippingMethod").isNotEmpty());
	}

	@Test
	void rejectsAnEmptyCart() throws Exception {
		String token = register("quote-empty@example.com");
		String addressId = addressOf(userIdOf(token));

		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(addressId, "STANDARD", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void requiresTheAddressToBelongToTheCustomer() throws Exception {
		String owner = register("quote-owner@example.com");
		String other = register("quote-other@example.com");
		String ownerAddress = addressOf(userIdOf(owner));
		addItem(other, stocked, 1);

		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + other)
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(ownerAddress, "STANDARD", null)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void rejectsAQuantityTheStockNoLongerCovers() throws Exception {
		String token = register("quote-stock@example.com");
		String addressId = addressOf(userIdOf(token));
		addItem(token, scarce, 2);
		addItem(token, stocked, 1);

		scarce.setStockQuantity(1);
		variantRepository.save(scarce);

		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(addressId, "STANDARD", null)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
			.andExpect(jsonPath("$.message").value("Only 1 left in stock"));
	}

	@Test
	void rejectsAnArchivedProduct() throws Exception {
		String token = register("quote-archived@example.com");
		String addressId = addressOf(userIdOf(token));
		addItem(token, stocked, 1);

		shirt.setStatus(ProductEntity.Status.ARCHIVED);
		productRepository.save(shirt);

		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(addressId, "STANDARD", null)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.message").value("Checkout Shirt is no longer available"));
	}

	@Test
	void rejectsAPromotionCodeUntilPromotionsExist() throws Exception {
		String token = register("quote-promo@example.com");
		String addressId = addressOf(userIdOf(token));
		addItem(token, stocked, 1);

		mockMvc.perform(post("/api/v1/checkout/quote")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(quoteBody(addressId, "STANDARD", "SUMMER10")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.promotionCode").isNotEmpty());
	}

	/* --- helpers ----------------------------------------------------------- */

	private String quoteBody(String addressId, String shippingMethod, String promotionCode) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("addressId", addressId);
		body.put("shippingMethod", shippingMethod);
		if (promotionCode != null) {
			body.put("promotionCode", promotionCode);
		}
		return toJson(body);
	}

	private static String toJson(Map<String, Object> body) {
		StringBuilder json = new StringBuilder("{");
		body.forEach((name, value) -> {
			if (json.length() > 1) {
				json.append(',');
			}
			json.append('"').append(name).append("\":\"").append(value).append('"');
		});
		return json.append('}').toString();
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

	private int stockOf(ProductVariantEntity variant) {
		Integer stock = jdbcTemplate.queryForObject(
			"select stock_quantity from product_variants where id = ?", Integer.class,
			variant.getId());
		return stock == null ? -1 : stock;
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
