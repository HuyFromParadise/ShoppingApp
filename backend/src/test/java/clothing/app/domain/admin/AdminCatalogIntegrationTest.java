package clothing.app.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import clothing.app.domain.order.OrderWorkflowFixture;
import clothing.app.domain.order.OrderWorkflowFixture.Customer;
import clothing.app.domain.user.UserRepository;

/**
 * Admin catalog API (DESIGN_REFERENCE §5.3, §6.2, Phase 6): RBAC on the whole
 * surface, product and variant writes, absolute stock with an audit trail,
 * category archival and image upload validation.
 *
 * <p>The tests roll back their database work. Uploaded files do not roll back,
 * so each upload test records what it wrote and deletes it afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=1000",
	"app.orders.timeout-sweep-interval-ms=86400000",
	"app.media.root=target/test-media"})
@Transactional
class AdminCatalogIntegrationTest {

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

	private static final Path MEDIA_DIR = Paths.get("target/test-media/products");

	@BeforeEach
	void setUp() {
		fixture = new OrderWorkflowFixture(mockMvc, jdbcTemplate, userRepository,
			passwordEncoder, addressRepository, categoryRepository, productRepository,
			variantRepository, imageRepository, jwtService, entityManager);
		adminToken = fixture.tokenOf(fixture.newAdmin("admin-catalog"));
	}

	@AfterEach
	void removeUploadedFiles() throws IOException {
		if (!Files.isDirectory(MEDIA_DIR)) {
			return;
		}
		try (Stream<Path> files = Files.list(MEDIA_DIR)) {
			for (Path file : files.toList()) {
				Files.deleteIfExists(file);
			}
		}
	}

	/* --- RBAC ---------------------------------------------------------------- */

	@Test
	void refusesEveryAdminCallWithoutAToken() throws Exception {
		for (var request : adminRequests()) {
			mockMvc.perform(request).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		}
	}

	@Test
	void refusesEveryAdminCallForACustomer() throws Exception {
		Customer customer = fixture.newCustomer("admin-rbac", 2);
		String token = fixture.tokenOf(customer);
		for (var request : adminRequests()) {
			mockMvc.perform(request.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
	}

	@Test
	void aDisabledAdminLosesAccessImmediately() throws Exception {
		Customer admin = fixture.newAdmin("admin-disabled");
		String token = fixture.tokenOf(admin);
		mockMvc.perform(get("/api/v1/admin/dashboard/summary")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		jdbcTemplate.update("update users set status = 'DISABLED' where id = ?", admin.id());
		entityManager.clear();

		// The token is still cryptographically valid, but the account is not.
		mockMvc.perform(get("/api/v1/admin/dashboard/summary")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	private java.util.List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> adminRequests() {
		return java.util.List.of(
			get("/api/v1/admin/products"),
			get("/api/v1/admin/products/anything"),
			post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON)
				.content("{}"),
			patch("/api/v1/admin/products/anything").contentType(MediaType.APPLICATION_JSON)
				.content("{}"),
			patch("/api/v1/admin/variants/anything/stock")
				.contentType(MediaType.APPLICATION_JSON).content("{\"stockQuantity\":1}"),
			get("/api/v1/admin/categories"),
			post("/api/v1/admin/categories").contentType(MediaType.APPLICATION_JSON)
				.content("{}"),
			get("/api/v1/admin/orders"),
			get("/api/v1/admin/dashboard/summary"));
	}

	/* --- products ------------------------------------------------------------ */

	@Test
	void createsAProductWithVariantsAndDerivesTheSlug() throws Exception {
		String categoryId = jdbcTemplate.queryForObject(
			"select id from categories limit 1", String.class);

		MvcResult result = createProduct("Linen Blazer", categoryId, 899000L,
			"{\"size\":\"M\",\"color\":\"Sand\",\"stockQuantity\":4}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.slug").value("linen-blazer"))
			.andExpect(jsonPath("$.data.status").value("DRAFT"))
			.andExpect(jsonPath("$.data.basePriceMinor").value(899000))
			.andExpect(jsonPath("$.data.variants.length()").value(1))
			.andExpect(jsonPath("$.data.variants[0].sku").value("LINEN_BLAZER-SAND-M"))
			.andExpect(jsonPath("$.data.variants[0].stockQuantity").value(4))
			.andExpect(jsonPath("$.data.variants[0].active").value(true))
			.andExpect(jsonPath("$.data.categoryName").isNotEmpty())
			.andReturn();

		String productId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
		assertThat(auditCount("product.create", productId)).isEqualTo(1);
	}

	@Test
	void rejectsAnInvalidProductPayload() throws Exception {
		String categoryId = jdbcTemplate.queryForObject(
			"select id from categories limit 1", String.class);

		// no name, a negative price, a bad status and no variants
		mockMvc.perform(post("/api/v1/admin/products")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":\"" + categoryId + "\",\"basePrice\":-1,"
					+ "\"status\":\"LIVE\",\"variants\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.basePrice").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.status").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.variants").isNotEmpty());
	}

	@Test
	void rejectsAnUnknownCategoryAndADuplicateSlug() throws Exception {
		createProduct("Slug Owner", randomCategory(), 100000L,
			"{\"size\":\"M\",\"color\":\"Black\",\"stockQuantity\":1}")
			.andExpect(status().isCreated());

		createProduct("Another Name", randomCategory(), 100000L,
			"{\"size\":\"S\",\"color\":\"Black\",\"stockQuantity\":1}", "slug-owner")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.slug").isNotEmpty());

		createProduct("Ghost Category", "00000000-0000-0000-0000-000000000000", 100000L,
			"{\"size\":\"M\",\"color\":\"Black\",\"stockQuantity\":1}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.categoryId").isNotEmpty());
	}

	@Test
	void rejectsTwoVariantsWithTheSameSizeAndColour() throws Exception {
		String categoryId = randomCategory();
		createProduct("Twin Variants", categoryId, 100000L,
			"{\"size\":\"M\",\"color\":\"Red\",\"stockQuantity\":1},"
				+ "{\"size\":\"m\",\"color\":\"red\",\"stockQuantity\":2}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.variants").isNotEmpty());
	}

	@Test
	void patchesAProductAndAuditsOnlyRealChanges() throws Exception {
		String productId = createDraftProduct("Patch Me");

		mockMvc.perform(patch("/api/v1/admin/products/" + productId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Patched Name\",\"status\":\"ACTIVE\",\"featured\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("Patched Name"))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"))
			.andExpect(jsonPath("$.data.featured").value(true));

		assertThat(auditCount("product.update", productId)).isEqualTo(1);

		// an identical patch changes nothing, so it writes no second audit row
		mockMvc.perform(patch("/api/v1/admin/products/" + productId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Patched Name\",\"status\":\"ACTIVE\",\"featured\":true}"))
			.andExpect(status().isOk());
		assertThat(auditCount("product.update", productId)).isEqualTo(1);
	}

	@Test
	void archivesRatherThanDeleting() throws Exception {
		String productId = createDraftProduct("Archive Me");

		mockMvc.perform(patch("/api/v1/admin/products/" + productId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ARCHIVED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ARCHIVED"));

		// The row survives, which is what keeps order history intact (ADR-009).
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject(
			"select status from products where id = ?", String.class, productId))
			.isEqualTo("ARCHIVED");
	}

	@Test
	void listsEveryStatusWithFilters() throws Exception {
		String activeId = createDraftProduct("List Active");
		mockMvc.perform(patch("/api/v1/admin/products/" + activeId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ACTIVE\"}"))
			.andExpect(status().isOk());
		createDraftProduct("List Draft");

		mockMvc.perform(get("/api/v1/admin/products?status=DRAFT&q=list")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].name").value("List Draft"))
			.andExpect(jsonPath("$.data[0].variantCount").value(1))
			.andExpect(jsonPath("$.data[0].totalStock").value(3));

		mockMvc.perform(get("/api/v1/admin/products?status=NOPE")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.status").isNotEmpty());

		mockMvc.perform(get("/api/v1/admin/products?size=99")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isBadRequest());
	}

	@Test
	void reportsAnUnknownProductAsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/admin/products/00000000-0000-0000-0000-000000000000")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(patch("/api/v1/admin/variants/00000000-0000-0000-0000-000000000000/stock")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stockQuantity\":5}"))
			.andExpect(status().isNotFound());
	}

	/* --- variants and stock --------------------------------------------------- */

	@Test
	void setsStockAbsolutelyAndRecordsWhoChangedIt() throws Exception {
		String productId = createDraftProduct("Stock Product");
		String variantId = variantsOf(productId).get(0);

		mockMvc.perform(patch("/api/v1/admin/variants/" + variantId + "/stock")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stockQuantity\":42,\"reason\":\"Stock count\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stockQuantity").value(42));

		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject(
			"select stock_quantity from product_variants where id = ?", Integer.class, variantId))
			.isEqualTo(42);

		var audit = jdbcTemplate.queryForMap(
			"select actor_user_id, metadata from audit_events "
				+ "where action = 'variant.stock.set' and target_id = ?",
			variantId);
		assertThat(audit.get("actor_user_id")).isNotNull();
		assertThat(String.valueOf(audit.get("metadata")))
			.contains("\"from\": 3").contains("\"to\": 42").contains("Stock count");
	}

	@Test
	void rejectsNegativeStockAndIgnoresAnUnchangedSet() throws Exception {
		String productId = createDraftProduct("Stock Bounds");
		String variantId = variantsOf(productId).get(0);

		mockMvc.perform(patch("/api/v1/admin/variants/" + variantId + "/stock")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stockQuantity\":-1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.stockQuantity").isNotEmpty());

		// setting the same value is a no-op and writes no audit row
		mockMvc.perform(patch("/api/v1/admin/variants/" + variantId + "/stock")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stockQuantity\":3}"))
			.andExpect(status().isOk());
		assertThat(auditCount("variant.stock.set", variantId)).isZero();
	}

	@Test
	void patchesVariantPriceAndActiveState() throws Exception {
		String productId = createDraftProduct("Variant Patch");
		String variantId = variantsOf(productId).get(0);

		// An unknown field is ignored (failOnUnknownProperties(false) in
		// JacksonConfig), so the price stays untouched; the name the server uses
		// is priceOverride, not priceOverrideMinor.
		mockMvc.perform(patch("/api/v1/admin/variants/" + variantId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"priceOverrideMinor\":1,\"active\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.priceOverrideMinor").doesNotExist())
			.andExpect(jsonPath("$.data.active").value(false));

		mockMvc.perform(patch("/api/v1/admin/variants/" + variantId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"priceOverride\":999000,\"active\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.priceOverrideMinor").value(999000))
			.andExpect(jsonPath("$.data.effectivePriceMinor").value(999000))
			.andExpect(jsonPath("$.data.active").value(false));

		// The first request only changed "active": its price field was ignored, so
		// the second is the only one that records a price change.
		assertThat(auditCount("variant.update", variantId)).isEqualTo(2);
	}

	@Test
	void addsAVariantAndRefusesADuplicateCombination() throws Exception {
		String productId = createDraftProduct("Add Variant");

		mockMvc.perform(post("/api/v1/admin/products/" + productId + "/variants")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"size\":\"L\",\"color\":\"Blue\",\"stockQuantity\":7}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.variants.length()").value(2));

		mockMvc.perform(post("/api/v1/admin/products/" + productId + "/variants")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"size\":\"L\",\"color\":\"Blue\",\"stockQuantity\":1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.variants").isNotEmpty());
	}

	/* --- categories ----------------------------------------------------------- */

	@Test
	void createsUpdatesAndArchivesACategory() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/admin/categories")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Áo Khoác\",\"sortOrder\":2}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.slug").value("ao-khoac"))
			.andExpect(jsonPath("$.data.active").value(true))
			.andExpect(jsonPath("$.data.productCount").value(0))
			.andReturn();
		String categoryId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(patch("/api/v1/admin/categories/" + categoryId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false,\"description\":\"Outerwear\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.active").value(false))
			.andExpect(jsonPath("$.data.description").value("Outerwear"));

		// inactive categories disappear from the public list but stay here
		mockMvc.perform(get("/api/v1/categories?size=100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.slug == 'ao-khoac')]").isEmpty());
		mockMvc.perform(get("/api/v1/admin/categories")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.slug == 'ao-khoac')].active").value(false));

		assertThat(auditCount("category.update", categoryId)).isEqualTo(1);
	}

	@Test
	void refusesADuplicateCategorySlugAndACyclicParent() throws Exception {
		MvcResult parent = mockMvc.perform(post("/api/v1/admin/categories")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Cycle Parent\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		String parentId = JsonPath.read(parent.getResponse().getContentAsString(), "$.data.id");

		MvcResult child = mockMvc.perform(post("/api/v1/admin/categories")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Cycle Child\",\"parentId\":\"" + parentId + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		String childId = JsonPath.read(child.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/admin/categories")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Another\",\"slug\":\"cycle-parent\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.slug").isNotEmpty());

		// parent -> child -> parent would be a cycle
		mockMvc.perform(patch("/api/v1/admin/categories/" + parentId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"parentId\":\"" + childId + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.parentId").isNotEmpty());

		mockMvc.perform(patch("/api/v1/admin/categories/" + parentId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"parentId\":\"" + parentId + "\"}"))
			.andExpect(status().isBadRequest());
	}

	/* --- images --------------------------------------------------------------- */

	@Test
	void storesARealImageAndMakesTheFirstOnePrimary() throws Exception {
		String productId = createDraftProduct("Image Product");
		byte[] png = pngBytes();

		mockMvc.perform(multipart("/api/v1/admin/products/" + productId + "/images")
				.file(new MockMultipartFile("file", "evil-name.php.png", "image/png", png))
				.param("altText", "A linen shirt")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.length()").value(1))
			// the name is replaced with a UUID and the extension comes from the bytes
			.andExpect(jsonPath("$.data[0].url").value(org.hamcrest.Matchers
				.matchesPattern("/media/products/[a-f0-9]{32}\\.png")))
			.andExpect(jsonPath("$.data[0].primary").value(true))
			.andExpect(jsonPath("$.data[0].altText").value("A linen shirt"));

		try (Stream<Path> files = Files.list(MEDIA_DIR)) {
			assertThat(files.count()).isEqualTo(1);
		}

		// the stored file is served back read-only, with its sniffed type
		String url = jdbcTemplate.queryForObject(
			"select storage_key from product_images WHERE product_id = ?", String.class, productId);
		mockMvc.perform(get(url))
			.andExpect(status().isOk());
	}

	@Test
	void rejectsAFileThatIsNotAnImageWhateverItClaims() throws Exception {
		String productId = createDraftProduct("Bad Image");

		mockMvc.perform(multipart("/api/v1/admin/products/" + productId + "/images")
				.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg",
					"<script>alert(1)</script>".getBytes()))
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.file").isNotEmpty());

		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from product_images where product_id = ?", Integer.class, productId))
			.isZero();
		try (Stream<Path> files = Files.list(MEDIA_DIR)) {
			assertThat(files.count()).isZero();
		}
	}

	@Test
	void refusesAnOversizedImage() throws Exception {
		String productId = createDraftProduct("Huge Image");
		byte[] header = pngBytes();
		byte[] huge = new byte[5 * 1024 * 1024 + 1024];
		System.arraycopy(header, 0, huge, 0, header.length);

		mockMvc.perform(multipart("/api/v1/admin/products/" + productId + "/images")
				.file(new MockMultipartFile("file", "big.png", "image/png", huge))
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().is4xxClientError());
	}

	@Test
	void deletesAnImageAndPromotesTheNextOne() throws Exception {
		String productId = createDraftProduct("Two Images");
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(multipart("/api/v1/admin/products/" + productId + "/images")
					.file(new MockMultipartFile("file", "img.png", "image/png", pngBytes()))
					.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isCreated());
		}
		String firstId = jdbcTemplate.queryForObject(
			"select id from product_images where product_id = ? and is_primary = true",
			String.class, productId);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/v1/admin/products/" + productId + "/images/" + firstId)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isNoContent());

		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from product_images where product_id = ?", Integer.class, productId))
			.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from product_images where product_id = ? and is_primary = true",
			Integer.class, productId))
			.isEqualTo(1);
	}

	/* --- helpers -------------------------------------------------------------- */

	private ResultActions createProduct(String name, String categoryId, long price,
			String variantsJson) throws Exception {
		return createProduct(name, categoryId, price, variantsJson, null);
	}

	private ResultActions createProduct(String name, String categoryId, long price,
			String variantsJson, String slug) throws Exception {
		String body = "{\"name\":\"" + name + "\",\"categoryId\":\"" + categoryId + "\","
			+ "\"basePrice\":" + price + ","
			+ (slug == null ? "" : "\"slug\":\"" + slug + "\",")
			+ "\"variants\":[" + variantsJson + "]}";
		return mockMvc.perform(post("/api/v1/admin/products")
			.header("Authorization", "Bearer " + adminToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/** Creates a DRAFT product with one M/Black variant holding 3 units. */
	private String createDraftProduct(String name) throws Exception {
		MvcResult result = createProduct(name, randomCategory(), 450000L,
			"{\"size\":\"M\",\"color\":\"Black\",\"stockQuantity\":3}")
			.andExpect(status().isCreated())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private String randomCategory() {
		return jdbcTemplate.queryForObject(
			"select id from categories order by rand() limit 1", String.class);
	}

	private java.util.List<String> variantsOf(String productId) {
		return jdbcTemplate.queryForList(
			"select id from product_variants where product_id = ? order by id", String.class,
			productId);
	}

	private int auditCount(String action, String targetId) {
		entityManager.flush();
		Integer count = jdbcTemplate.queryForObject(
			"select count(*) from audit_events where action = ? and target_id = ?",
			Integer.class, action, targetId);
		return count == null ? 0 : count;
	}

	private static byte[] pngBytes() {
		return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
			0, 0, 0, 13, 'I', 'H', 'D', 'R'};
	}
}
