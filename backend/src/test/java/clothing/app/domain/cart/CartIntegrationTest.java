package clothing.app.domain.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;

/**
 * Cart contract against the real MySQL test schema (DESIGN_REFERENCE §5.3,
 * §5.4): lazy creation, one line per variant, quantity caps, stock, price
 * snapshots, availability and ownership.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "RATE_LIMIT_AUTH_CAPACITY=1000")
@Transactional
class CartIntegrationTest {

	private static final long BASE_PRICE = 499000L;

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

	private ProductEntity shirt;
	private ProductVariantEntity stocked;
	private ProductVariantEntity lowStock;
	private ProductVariantEntity bulky;
	private ProductVariantEntity discounted;
	private ProductVariantEntity inactive;
	private ProductVariantEntity draft;

	@BeforeEach
	void seedCatalog() {
		CategoryEntity category = categoryRepository.save(category("Cart Tops", "cart-tops"));
		shirt = productRepository.save(product(category, "Cart Shirt", "cart-shirt",
			ProductEntity.Status.ACTIVE));

		stocked = variantRepository.save(variant(shirt, "CART-M-WHITE", "M", "White", null, 5, true));
		lowStock = variantRepository.save(variant(shirt, "CART-S-WHITE", "S", "White", null, 1, true));
		bulky = variantRepository.save(variant(shirt, "CART-L-WHITE", "L", "White", null, 20, true));
		discounted = variantRepository.save(
			variant(shirt, "CART-XL-WHITE", "XL", "White", 399000L, 3, true));
		inactive = variantRepository.save(variant(shirt, "CART-M-BLACK", "M", "Black", null, 5, false));

		ProductEntity draftProduct = productRepository.save(product(category, "Draft Shirt",
			"cart-draft-shirt", ProductEntity.Status.DRAFT));
		draft = variantRepository.save(
			variant(draftProduct, "CART-DRAFT-M", "M", "Grey", null, 5, true));

		ProductImageEntity image = new ProductImageEntity();
		image.setProductId(shirt.getId());
		image.setStorageKey("/images/cart-shirt.jpg");
		image.setAltText("Cart Shirt");
		image.setSortOrder(0);
		image.setIsPrimary(Boolean.TRUE);
		imageRepository.save(image);
	}

	/* --- helpers ----------------------------------------------------------- */

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

	private MvcResult addItem(String token, ProductVariantEntity variant, int quantity)
			throws Exception {
		return mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + variant.getId() + "\",\"quantity\":" + quantity + "}"))
			.andReturn();
	}

	private String itemIdOf(MvcResult result) throws Exception {
		return com.jayway.jsonpath.JsonPath.read(
			result.getResponse().getContentAsString(), "$.data.items[0].id");
	}

	private String cartId(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/cart")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return com.jayway.jsonpath.JsonPath.read(
			result.getResponse().getContentAsString(), "$.data.id");
	}

	/* --- tests ------------------------------------------------------------- */

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/cart"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/cart/items")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"x\",\"quantity\":1}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/cart"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createsAnEmptyCartLazilyAndReusesIt() throws Exception {
		String token = register("cart-lazy@example.com");

		MvcResult first = mockMvc.perform(get("/api/v1/cart")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").isNotEmpty())
			.andExpect(jsonPath("$.data.items.length()").value(0))
			.andExpect(jsonPath("$.data.itemCount").value(0))
			.andExpect(jsonPath("$.data.subtotalMinor").value(0))
			.andExpect(jsonPath("$.data.currency").value("VND"))
			.andReturn();
		String id = com.jayway.jsonpath.JsonPath.read(
			first.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.id").value(id));
	}

	@Test
	void addsAnItemAndReturnsTheWholeCart() throws Exception {
		String token = register("cart-add@example.com");

		mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + stocked.getId() + "\",\"quantity\":2}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].variantId").value(stocked.getId()))
			.andExpect(jsonPath("$.data.items[0].productName").value("Cart Shirt"))
			.andExpect(jsonPath("$.data.items[0].productSlug").value("cart-shirt"))
			.andExpect(jsonPath("$.data.items[0].imageUrl").value("/images/cart-shirt.jpg"))
			.andExpect(jsonPath("$.data.items[0].sku").value("CART-M-WHITE"))
			.andExpect(jsonPath("$.data.items[0].size").value("M"))
			.andExpect(jsonPath("$.data.items[0].color").value("White"))
			.andExpect(jsonPath("$.data.items[0].unitPriceMinor").value(BASE_PRICE))
			.andExpect(jsonPath("$.data.items[0].quantity").value(2))
			.andExpect(jsonPath("$.data.items[0].lineTotalMinor").value(BASE_PRICE * 2))
			.andExpect(jsonPath("$.data.items[0].stockQuantity").value(5))
			.andExpect(jsonPath("$.data.items[0].available").value(true))
			.andExpect(jsonPath("$.data.itemCount").value(2))
			.andExpect(jsonPath("$.data.subtotalMinor").value(BASE_PRICE * 2))
			.andExpect(jsonPath("$.data.currency").value("VND"));
	}

	@Test
	void mergesTheSameVariantIntoOneLine() throws Exception {
		String token = register("cart-merge@example.com");

		addItem(token, bulky, 2);
		mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + bulky.getId() + "\",\"quantity\":3}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].quantity").value(5))
			.andExpect(jsonPath("$.data.itemCount").value(5))
			.andExpect(jsonPath("$.data.subtotalMinor").value(BASE_PRICE * 5));
	}

	@Test
	void keepsDifferentVariantsOnSeparateLines() throws Exception {
		String token = register("cart-lines@example.com");

		addItem(token, stocked, 1);
		addItem(token, discounted, 2);

		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.itemCount").value(3))
			.andExpect(jsonPath("$.data.subtotalMinor").value(BASE_PRICE + 399000L * 2));
	}

	@Test
	void refusesMoreThanTenPerLine() throws Exception {
		String token = register("cart-cap@example.com");

		// 11 in one request is rejected by the DTO
		mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + bulky.getId() + "\",\"quantity\":11}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.quantity").isNotEmpty());

		// 6 + 6 on the same line would be 12
		addItem(token, bulky, 6);
		mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + bulky.getId() + "\",\"quantity\":6}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.quantity").isNotEmpty());

		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items[0].quantity").value(6));
	}

	@Test
	void refusesMoreThanTheStock() throws Exception {
		String token = register("cart-stock@example.com");

		mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + lowStock.getId() + "\",\"quantity\":2}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
			.andExpect(jsonPath("$.message").value("Only 1 left in stock"));

		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void refusesAnInactiveVariantOrAnInactiveProduct() throws Exception {
		String token = register("cart-inactive@example.com");

		mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + inactive.getId() + "\",\"quantity\":1}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + draft.getId() + "\",\"quantity\":1}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void usesTheVariantPriceOverride() throws Exception {
		String token = register("cart-price@example.com");

		addItem(token, discounted, 2);

		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items[0].unitPriceMinor").value(399000))
			.andExpect(jsonPath("$.data.items[0].lineTotalMinor").value(798000));
	}

	@Test
	void updatesTheQuantity() throws Exception {
		String token = register("cart-update@example.com");
		String itemId = itemIdOf(addItem(token, stocked, 1));

		mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":4}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].quantity").value(4))
			.andExpect(jsonPath("$.data.subtotalMinor").value(BASE_PRICE * 4));
	}

	@Test
	void refusesAnUpdateBeyondTheStockOrBelowOne() throws Exception {
		String token = register("cart-update-bad@example.com");
		String itemId = itemIdOf(addItem(token, stocked, 1));

		mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":6}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));

		mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void removesAnItem() throws Exception {
		String token = register("cart-remove@example.com");
		String itemId = itemIdOf(addItem(token, stocked, 2));

		mockMvc.perform(delete("/api/v1/cart/items/" + itemId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items.length()").value(0))
			.andExpect(jsonPath("$.data.itemCount").value(0));
	}

	@Test
	void clearsTheCart() throws Exception {
		String token = register("cart-clear@example.com");
		addItem(token, stocked, 1);
		addItem(token, bulky, 2);

		mockMvc.perform(delete("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items.length()").value(0))
			.andExpect(jsonPath("$.data.subtotalMinor").value(0));
	}

	@Test
	void neverTouchesAnotherUsersItem() throws Exception {
		String owner = register("cart-owner@example.com");
		String other = register("cart-other@example.com");
		String itemId = itemIdOf(addItem(owner, stocked, 2));

		mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
				.header("Authorization", "Bearer " + other)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":1}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(delete("/api/v1/cart/items/" + itemId)
				.header("Authorization", "Bearer " + other))
			.andExpect(status().isNotFound());

		// the other customer gets their own empty cart, not the owner's
		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + other))
			.andExpect(jsonPath("$.data.items.length()").value(0));
		// and the owner's line is untouched
		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + owner))
			.andExpect(jsonPath("$.data.items[0].quantity").value(2));
	}

	@Test
	void keepsALineButFlagsItWhenTheProductBecomesUnavailable() throws Exception {
		String token = register("cart-unavailable@example.com");
		addItem(token, stocked, 2);

		shirt.setStatus(ProductEntity.Status.ARCHIVED);
		productRepository.save(shirt);

		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].available").value(false));
	}

	@Test
	void flagsALineWhoseStockDroppedBelowTheQuantity() throws Exception {
		String token = register("cart-shrink@example.com");
		addItem(token, stocked, 5);

		stocked.setStockQuantity(2);
		variantRepository.save(stocked);

		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.items[0].available").value(false))
			.andExpect(jsonPath("$.data.items[0].stockQuantity").value(2));
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
			String color, Long priceOverride, int stock, boolean active) {
		ProductVariantEntity variant = new ProductVariantEntity();
		variant.setProductId(product.getId());
		variant.setSku(sku);
		variant.setSize(size);
		variant.setColor(color);
		variant.setPriceOverride(priceOverride);
		variant.setStockQuantity(stock);
		variant.setIsActive(active);
		return variant;
	}
}
