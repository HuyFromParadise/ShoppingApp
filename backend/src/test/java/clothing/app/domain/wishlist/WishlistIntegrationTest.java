package clothing.app.domain.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import clothing.app.domain.order.OrderWorkflowFixture;
import clothing.app.domain.order.OrderWorkflowFixture.Customer;
import clothing.app.domain.user.UserRepository;

/**
 * Wishlist (PROJECT_SPEC §7, P1): saving a product, idempotence, product-keyed
 * removal, and the ownership rule that one customer can never see another's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=1000",
	"app.orders.timeout-sweep-interval-ms=86400000"})
@Transactional
class WishlistIntegrationTest {

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
	void startsEmptyAndSavesAProductWithItsCurrentPrice() throws Exception {
		Customer customer = fixture.newCustomer("wish-save", 4);
		String token = fixture.tokenOf(customer);
		String productId = productOf(customer.variantId());

		mockMvc.perform(get("/api/v1/wishlist").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));

		mockMvc.perform(post("/api/v1/wishlist/items/" + productId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].productId").value(productId))
			.andExpect(jsonPath("$.data[0].priceMinor")
				.value(OrderWorkflowFixture.BASE_PRICE))
			.andExpect(jsonPath("$.data[0].inStock").value(true))
			.andExpect(jsonPath("$.data[0].purchasable").value(true))
			.andExpect(jsonPath("$.data[0].productSlug").isNotEmpty());

		mockMvc.perform(get("/api/v1/wishlist/items/" + productId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").value(true));
	}

	@Test
	void savingTwiceDoesNotDuplicateTheProduct() throws Exception {
		Customer customer = fixture.newCustomer("wish-twice", 2);
		String token = fixture.tokenOf(customer);
		String productId = productOf(customer.variantId());

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/wishlist/items/" + productId)
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		}

		mockMvc.perform(get("/api/v1/wishlist").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.length()").value(1));
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from wishlist_items", Integer.class)).isEqualTo(1);
	}

	@Test
	void aProductWithSeveralVariantsIsSavedOnce() throws Exception {
		Customer customer = fixture.newCustomer("wish-multi", 3);
		String token = fixture.tokenOf(customer);
		String productId = productOf(customer.variantId());

		mockMvc.perform(post("/api/v1/wishlist/items/" + productId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		// a second variant of the same product must not create a second entry
		jdbcTemplate.update(
			"insert into product_variants (id, product_id, sku, size, color, stock_quantity, "
				+ "is_active, created_at, updated_at) values (uuid(), ?, 'WISH-2', 'L', 'Navy', 2, "
				+ "true, now(6), now(6))",
			productId);
		entityManager.clear();

		mockMvc.perform(post("/api/v1/wishlist/items/" + productId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void removesByProductIdAndStaysIdempotent() throws Exception {
		Customer customer = fixture.newCustomer("wish-remove", 3);
		String token = fixture.tokenOf(customer);
		String productId = productOf(customer.variantId());

		mockMvc.perform(post("/api/v1/wishlist/items/" + productId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(delete("/api/v1/wishlist/items/" + productId)
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
		}
	}

	@Test
	void keepsWishlistsPrivateToTheirOwner() throws Exception {
		Customer owner = fixture.newCustomer("wish-owner", 2);
		String ownerToken = fixture.tokenOf(owner);
		String productId = productOf(owner.variantId());
		mockMvc.perform(post("/api/v1/wishlist/items/" + productId)
				.header("Authorization", "Bearer " + ownerToken))
			.andExpect(status().isOk());

		Customer other = fixture.newCustomer("wish-other", 2);
		String otherToken = fixture.tokenOf(other);
		mockMvc.perform(get("/api/v1/wishlist").header("Authorization", "Bearer " + otherToken))
			.andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(get("/api/v1/wishlist/items/" + productId)
				.header("Authorization", "Bearer " + otherToken))
			.andExpect(jsonPath("$.data").value(false));

		// one customer's delete cannot take another's entry
		mockMvc.perform(delete("/api/v1/wishlist/items/" + productId)
				.header("Authorization", "Bearer " + otherToken))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/wishlist").header("Authorization", "Bearer " + ownerToken))
			.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/wishlist")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/wishlist/items/whatever"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/wishlist/items/whatever"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refusesAnUnknownProduct() throws Exception {
		String token = fixture.registerCustomer("wish-unknown");
		mockMvc.perform(post("/api/v1/wishlist/items/00000000-0000-0000-0000-000000000000")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void showsAnArchivedProductAsNotPurchasable() throws Exception {
		Customer customer = fixture.newCustomer("wish-archived", 2);
		String token = fixture.tokenOf(customer);
		String productId = productOf(customer.variantId());
		mockMvc.perform(post("/api/v1/wishlist/items/" + productId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		jdbcTemplate.update("update products set status = 'ARCHIVED' where id = ?", productId);
		entityManager.clear();

		mockMvc.perform(get("/api/v1/wishlist").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].purchasable").value(false));
	}

	private String productOf(String variantId) {
		return jdbcTemplate.queryForObject(
			"select product_id from product_variants where id = ?", String.class, variantId);
	}
}
